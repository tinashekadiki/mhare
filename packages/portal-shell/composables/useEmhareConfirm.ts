import Swal, { type SweetAlertOptions } from "sweetalert2";

function resolveAlertTarget(): SweetAlertOptions["target"] {
  if (import.meta.server) return "body";

  const activeDialog = document.activeElement?.closest<HTMLElement>(
    '[role="dialog"][data-state="open"]',
  );
  if (activeDialog && !activeDialog.classList.contains("swal2-popup")) return activeDialog;

  const openDialogs = Array.from(
    document.querySelectorAll<HTMLElement>('[role="dialog"][data-state="open"]'),
  ).filter((dialog) => !dialog.classList.contains("swal2-popup"));

  return openDialogs.at(-1) ?? "body";
}

function alertOptions(options: SweetAlertOptions): SweetAlertOptions {
  return { ...options, target: resolveAlertTarget() };
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

export function useEmhareConfirm() {
  async function confirmAction(options: {
    title: string;
    text?: string;
    confirmButtonText?: string;
    cancelButtonText?: string;
    icon?: "warning" | "question" | "info" | "error" | "success";
    destructive?: boolean;
  }) {
    const result = await Swal.fire(
      alertOptions({
        title: options.title,
        text: options.text,
        icon: options.icon ?? (options.destructive ? "warning" : "question"),
        showCancelButton: true,
        confirmButtonText: options.confirmButtonText ?? "Continue",
        cancelButtonText: options.cancelButtonText ?? "Cancel",
        confirmButtonColor: options.destructive ? "#dc2626" : "var(--color-uzazure-600)",
      }),
    );
    return result.isConfirmed;
  }

  async function showSuccess(title: string, text?: string) {
    await Swal.fire(alertOptions({ title, text, icon: "success" }));
  }

  async function showError(title: string, text?: string) {
    const messageLines = text
      ?.split("\n")
      .map((line) => line.replace(/^•\s*/, "").trim())
      .filter(Boolean);

    await Swal.fire(
      alertOptions({
        title,
        ...(messageLines && messageLines.length > 1
          ? {
              html: `<ul class="list-disc space-y-2 pl-5 text-left">${messageLines
                .map((line) => `<li>${escapeHtml(line)}</li>`)
                .join("")}</ul>`,
            }
          : { text }),
        icon: "error",
        confirmButtonColor: "var(--color-uzazure-600)",
      }),
    );
  }

  async function showActionGuidance(options: {
    title: string;
    description?: string;
    instructions: string[];
    actionLabel?: string;
  }) {
    const description = options.description
      ? `<p class="mb-3 text-left">${escapeHtml(options.description)}</p>`
      : "";
    const instructions = options.instructions
      .map((instruction) => `<li class="mb-2">${escapeHtml(instruction)}</li>`)
      .join("");

    const result = await Swal.fire(
      alertOptions({
        title: options.title,
        html: `${description}<ul class="list-disc pl-5 text-left">${instructions}</ul>`,
        icon: "info",
        showCancelButton: Boolean(options.actionLabel),
        confirmButtonText: options.actionLabel ?? "Understood",
        cancelButtonText: "Stay here",
        confirmButtonColor: "var(--color-uzazure-600)",
      }),
    );

    return result.isConfirmed;
  }

  return { confirmAction, showSuccess, showError, showActionGuidance };
}
