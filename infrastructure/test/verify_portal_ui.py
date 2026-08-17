#!/usr/bin/env python3

"""Author: Tinashe K

Live browser verification for the applicant application and admissions review flow.
The script creates isolated Keycloak and database fixtures and removes them on exit.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import urllib.parse
import urllib.request
import uuid

from playwright.sync_api import Page, expect, sync_playwright


KEYCLOAK_BASE_URL = os.getenv("KEYCLOAK_BASE_URL", "http://localhost:8099")
KEYCLOAK_REALM = os.getenv("KEYCLOAK_REALM", "emhare")
POSTGRES_CONTAINER = os.getenv("POSTGRES_CONTAINER", "emhare-postgres")
API_GATEWAY_BASE_URL = os.getenv("API_GATEWAY_BASE_URL", "http://localhost:18080")
APPLICANT_PORTAL_URL = os.getenv("APPLICANT_PORTAL_URL", "http://localhost:3001")
ADMIN_PORTAL_URL = os.getenv("ADMIN_PORTAL_URL", "http://localhost:3000")
TEST_RESULTS_DIRECTORY = Path(os.getenv("TEST_RESULTS_DIRECTORY", "test-results/enterprise-admissions-ui"))


class PortalUiFixture:
    def __init__(self) -> None:
        self.run_id = str(uuid.uuid4())
        self.email = f"portal-e2e-{self.run_id}@example.test"
        self.password = "Temporary-Portal-E2E-Password-42"
        self.admission_cycle_id = str(uuid.uuid4())
        self.academic_year_id = str(uuid.uuid4())
        self.intake_id = str(uuid.uuid4())
        self.application_type_id = str(uuid.uuid4())
        self.application_fee_id = str(uuid.uuid4())
        self.keycloak_user_id = ""
        self.core_user_id = ""
        self.application_id = ""
        self.application_number = ""
        self.admin_token = ""

    def setup(self) -> None:
        self.admin_token = self._keycloak_form_post(
            "/realms/master/protocol/openid-connect/token",
            {
                "grant_type": "password",
                "client_id": "admin-cli",
                "username": os.getenv("KEYCLOAK_ADMIN_USERNAME", "admin"),
                "password": os.getenv("KEYCLOAK_ADMIN_PASSWORD", "admin"),
            },
        )["access_token"]
        response_headers = self._keycloak_json_request(
            "POST",
            f"/admin/realms/{KEYCLOAK_REALM}/users",
            {
                "username": self.email,
                "email": self.email,
                "emailVerified": True,
                "enabled": True,
                "firstName": "Enterprise",
                "lastName": "Applicant",
                "credentials": [{"type": "password", "value": self.password, "temporary": False}],
            },
            return_headers=True,
        )
        self.keycloak_user_id = response_headers["Location"].rstrip("/").split("/")[-1]
        role_representations = [
            self._keycloak_json_request("GET", f"/admin/realms/{KEYCLOAK_REALM}/roles/{role_name}")
            for role_name in ("applicant", "admissions-officer", "finance-officer")
        ]
        self._keycloak_json_request(
            "POST",
            f"/admin/realms/{KEYCLOAK_REALM}/users/{self.keycloak_user_id}/role-mappings/realm",
            role_representations,
        )
        self._psql(
            "emhare_admissions",
            """
INSERT INTO admission_cycles (id, academic_year_id, intake_id, code, name, opens_at, closes_at, status, created_at, updated_at, version)
VALUES (:'admission_cycle_id'::uuid, :'academic_year_id'::uuid, :'intake_id'::uuid, 'PORTAL-E2E-' || left(:'admission_cycle_id', 8), 'August 2027 enterprise intake', now() - interval '1 day', now() + interval '30 days', 'OPEN', now(), now(), 0);
INSERT INTO application_types (id, code, name, requires_employment_history, requires_referees, is_active, created_at, updated_at, version)
VALUES (:'application_type_id'::uuid, 'PORTAL-E2E-' || left(:'application_type_id', 8), 'Undergraduate degree application', false, false, true, now(), now(), 0);
INSERT INTO application_fees (id, application_type_id, applicant_category_code, currency_code, amount, effective_from, effective_to, is_active, created_at, updated_at, version)
VALUES (:'application_fee_id'::uuid, :'application_type_id'::uuid, 'LOCAL', 'USD', 25.00, current_date - 1, null, true, now(), now(), 0);
""",
            {
                "admission_cycle_id": self.admission_cycle_id,
                "academic_year_id": self.academic_year_id,
                "intake_id": self.intake_id,
                "application_type_id": self.application_type_id,
                "application_fee_id": self.application_fee_id,
            },
        )

    def discover_created_records(self) -> None:
        if self.application_number:
            self.application_id = self._psql_value(
                "emhare_admissions",
                "SELECT id FROM applications WHERE application_number = :'application_number';",
                {"application_number": self.application_number},
            )
        self.core_user_id = self._psql_value(
            "emhare_core_identity",
            "SELECT id FROM users WHERE email = :'email';",
            {"email": self.email},
        )

    def cleanup(self) -> None:
        self.discover_created_records()
        if self.application_id:
            self._psql(
                "emhare_finance",
                """
DELETE FROM integration_outbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM integration_inbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM finance_receipts_aud WHERE id IN (SELECT receipt.id FROM finance_receipts receipt JOIN application_payments payment ON payment.id = receipt.application_payment_id WHERE payment.source_application_id = :'application_id'::uuid);
DELETE FROM finance_receipts WHERE application_payment_id IN (SELECT id FROM application_payments WHERE source_application_id = :'application_id'::uuid);
DELETE FROM application_payments_aud WHERE id IN (SELECT id FROM application_payments WHERE source_application_id = :'application_id'::uuid);
DELETE FROM application_payments WHERE source_application_id = :'application_id'::uuid;
DELETE FROM application_payment_references_aud WHERE id IN (SELECT id FROM application_payment_references WHERE source_application_id = :'application_id'::uuid);
DELETE FROM application_payment_references WHERE source_application_id = :'application_id'::uuid;
""",
                {"application_id": self.application_id},
            )
            self._psql(
                "emhare_admissions",
                """
DELETE FROM integration_outbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM integration_inbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM application_status_events_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_status_events WHERE application_id = :'application_id'::uuid;
DELETE FROM application_payment_references_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_payment_references WHERE application_id = :'application_id'::uuid;
DELETE FROM applications_aud WHERE id = :'application_id'::uuid;
DELETE FROM applications WHERE id = :'application_id'::uuid;
""",
                {"application_id": self.application_id},
            )
        if self.core_user_id:
            self._psql(
                "emhare_admissions",
                "DELETE FROM applicants_aud WHERE user_id = :'core_user_id'::uuid; DELETE FROM applicants WHERE user_id = :'core_user_id'::uuid;",
                {"core_user_id": self.core_user_id},
            )
            self._psql(
                "emhare_core_identity",
                """
DELETE FROM user_role_assignments_aud WHERE user_id = :'core_user_id'::uuid;
DELETE FROM user_role_assignments WHERE user_id = :'core_user_id'::uuid;
DELETE FROM login_events_aud WHERE user_id = :'core_user_id'::uuid;
DELETE FROM login_events WHERE user_id = :'core_user_id'::uuid;
DELETE FROM users_aud WHERE id = :'core_user_id'::uuid;
DELETE FROM users WHERE id = :'core_user_id'::uuid;
""",
                {"core_user_id": self.core_user_id},
            )
        self._psql(
            "emhare_admissions",
            """
DELETE FROM application_fees_aud WHERE id = :'application_fee_id'::uuid;
DELETE FROM application_fees WHERE id = :'application_fee_id'::uuid;
DELETE FROM application_types_aud WHERE id = :'application_type_id'::uuid;
DELETE FROM application_types WHERE id = :'application_type_id'::uuid;
DELETE FROM admission_cycles_aud WHERE id = :'admission_cycle_id'::uuid;
DELETE FROM admission_cycles WHERE id = :'admission_cycle_id'::uuid;
""",
            {
                "application_fee_id": self.application_fee_id,
                "application_type_id": self.application_type_id,
                "admission_cycle_id": self.admission_cycle_id,
            },
        )
        if self.keycloak_user_id and self.admin_token:
            try:
                self._keycloak_json_request(
                    "DELETE",
                    f"/admin/realms/{KEYCLOAK_REALM}/users/{self.keycloak_user_id}",
                )
            except Exception:
                pass

    def _keycloak_form_post(self, path: str, form: dict[str, str]) -> dict:
        request = urllib.request.Request(
            KEYCLOAK_BASE_URL + path,
            data=urllib.parse.urlencode(form).encode(),
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            method="POST",
        )
        with urllib.request.urlopen(request) as response:
            return json.load(response)

    def _keycloak_json_request(self, method: str, path: str, body=None, return_headers: bool = False):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(
            KEYCLOAK_BASE_URL + path,
            data=data,
            headers={
                "Authorization": f"Bearer {self.admin_token}",
                "Content-Type": "application/json",
            },
            method=method,
        )
        with urllib.request.urlopen(request) as response:
            if return_headers:
                return response.headers
            payload = response.read()
            return json.loads(payload) if payload else None

    def _psql(self, database: str, sql: str, variables: dict[str, str]) -> None:
        command = ["docker", "exec", "-i", POSTGRES_CONTAINER, "psql", "-q", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", database]
        for name, value in variables.items():
            command.extend(["-v", f"{name}={value}"])
        subprocess.run(command, input=sql, text=True, check=True, capture_output=True)

    def _psql_value(self, database: str, sql: str, variables: dict[str, str]) -> str:
        command = ["docker", "exec", "-i", POSTGRES_CONTAINER, "psql", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", database]
        for name, value in variables.items():
            command.extend(["-v", f"{name}={value}"])
        result = subprocess.run(command, input=sql, text=True, check=True, capture_output=True)
        return result.stdout.strip()


def redirect_api_requests(page: Page) -> None:
    def redirect(route) -> None:
        original_url = route.request.url
        redirected_url = original_url.replace("http://localhost:8080", API_GATEWAY_BASE_URL, 1)
        route.continue_(url=redirected_url)

    page.route("http://localhost:8080/api/**", redirect)


def complete_keycloak_login(page: Page, fixture: PortalUiFixture, destination_pattern: re.Pattern[str]) -> None:
    try:
        page.locator("#username").wait_for(state="visible", timeout=3_000)
        page.locator("#username").fill(fixture.email)
        page.locator("#password").fill(fixture.password)
        page.locator("#kc-login").click()
    except Exception:
        pass
    page.wait_for_url(destination_pattern, timeout=30_000)
    page.wait_for_load_state("networkidle")


def run_browser_flow(fixture: PortalUiFixture) -> None:
    TEST_RESULTS_DIRECTORY.mkdir(parents=True, exist_ok=True)
    console_errors: list[str] = []
    page_errors: list[str] = []

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 1000})

        applicant_page = context.new_page()
        redirect_api_requests(applicant_page)
        applicant_page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        applicant_page.on("pageerror", lambda error: page_errors.append(str(error)))
        applicant_page.goto(APPLICANT_PORTAL_URL, wait_until="networkidle")
        expect(applicant_page.get_by_role("heading", name="My applications")).to_be_visible()
        applicant_page.get_by_role("button", name="Sign in").click()
        applicant_page.wait_for_url(re.compile(r"http://localhost:8099/realms/emhare/.*"), timeout=30_000)
        complete_keycloak_login(applicant_page, fixture, re.compile(r"http://localhost:3001/.*"))

        expect(applicant_page.get_by_text("No applications yet")).to_be_visible()
        applicant_page.get_by_role("button", name="Start application").first.click()
        expect(applicant_page.get_by_role("heading", name="Start an application")).to_be_visible()
        applicant_page.get_by_label("First name").fill("Enterprise")
        applicant_page.get_by_label("Last name").fill("Applicant")
        applicant_page.get_by_label("Admission cycle").click()
        applicant_page.get_by_role("option", name=re.compile("August 2027 enterprise intake")).click()
        applicant_page.get_by_label("Application type").click()
        applicant_page.get_by_role("option", name=re.compile("Undergraduate degree application")).click()
        expect(applicant_page.get_by_text("US$25.00 must be confirmed by Finance before submission.")).to_be_visible()
        applicant_page.get_by_role("button", name="Create draft").click()
        expect(applicant_page.get_by_role("heading", name="Application started")).to_be_visible()
        applicant_page.get_by_role("button", name="OK").click()
        expect(applicant_page.get_by_text("Payment pending")).to_be_visible()
        payment_reference_locator = applicant_page.get_by_text(re.compile(r"^EMH-PAY-"))
        for _ in range(40):
            if payment_reference_locator.count() > 0 and payment_reference_locator.first.is_visible():
                break
            applicant_page.get_by_role("button", name="Refresh").click()
            applicant_page.wait_for_timeout(250)
        else:
            raise AssertionError("Finance payment reference was not projected within 10 seconds")
        application_number_locator = applicant_page.locator("h2", has_text=re.compile(r"^EMH-"))
        fixture.application_number = application_number_locator.first.inner_text()
        awaitable_screenshot = TEST_RESULTS_DIRECTORY / "applicant-payment-pending.png"
        applicant_page.screenshot(path=str(awaitable_screenshot), full_page=True)

        admin_page = context.new_page()
        redirect_api_requests(admin_page)
        admin_page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        admin_page.on("pageerror", lambda error: page_errors.append(str(error)))
        admin_page.goto(f"{ADMIN_PORTAL_URL}/operations/admissions", wait_until="domcontentloaded")
        complete_keycloak_login(admin_page, fixture, re.compile(r"http://localhost:3000/operations/admissions.*"))
        expect(admin_page.get_by_role("heading", name="Admissions review queue")).to_be_visible()
        expect(admin_page.get_by_text(fixture.application_number)).to_be_visible()
        expect(admin_page.get_by_role("button", name="Confirm payment")).to_have_count(0)
        application_row = admin_page.get_by_role("row").filter(has_text=fixture.application_number)
        application_row.get_by_role("button", name="Waive fee").click()
        admin_page.locator(".swal2-textarea").fill("Approved bursary waiver UI verification")
        admin_page.get_by_role("button", name="Authorise waiver").click()
        expect(application_row.get_by_text("Waived")).to_be_visible()
        admin_page.screenshot(path=str(TEST_RESULTS_DIRECTORY / "admin-finance-owned-queue.png"), full_page=True)

        applicant_page.bring_to_front()
        applicant_page.reload(wait_until="networkidle")
        expect(applicant_page.get_by_text("Waived", exact=True)).to_be_visible()
        applicant_page.get_by_role("button", name="Submit application").click()
        expect(applicant_page.get_by_role("heading", name="Submit application?")).to_be_visible()
        applicant_page.get_by_role("button", name="Submit application").last.click()
        expect(applicant_page.get_by_text("Submitted", exact=True)).to_be_visible()
        applicant_page.locator(".swal2-container").wait_for(state="detached", timeout=5_000)
        applicant_page.screenshot(path=str(TEST_RESULTS_DIRECTORY / "applicant-submitted.png"), full_page=True)

        browser.close()

    if console_errors or page_errors:
        raise AssertionError(
            "Browser errors detected:\n"
            + "\n".join([*(f"console: {error}" for error in console_errors), *(f"page: {error}" for error in page_errors)])
        )

    print(json.dumps({
        "result": "PASS",
        "applicationNumber": fixture.application_number,
        "applicantScreenshot": str(TEST_RESULTS_DIRECTORY / "applicant-submitted.png"),
        "adminScreenshot": str(TEST_RESULTS_DIRECTORY / "admin-finance-owned-queue.png"),
        "consoleErrors": 0,
        "pageErrors": 0,
    }, indent=2))


def main() -> None:
    fixture = PortalUiFixture()
    try:
        fixture.setup()
        run_browser_flow(fixture)
    finally:
        fixture.cleanup()


if __name__ == "__main__":
    main()
