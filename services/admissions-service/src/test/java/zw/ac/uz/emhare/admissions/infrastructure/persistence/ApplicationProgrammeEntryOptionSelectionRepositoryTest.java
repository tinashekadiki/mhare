package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeEntryOptionSelection;

/** @author Tinashe K */
class ApplicationProgrammeEntryOptionSelectionRepositoryTest {

    @Test
    void programmeChoiceQueryNavigatesTheEntityRelationshipToItsIdentifier() throws NoSuchMethodException {
        Method repositoryMethod = ApplicationProgrammeEntryOptionSelectionRepository.class.getMethod(
                "findAllByProgrammeChoice_IdAndDeletedAtIsNullOrderByPreferenceRankAsc", UUID.class);

        assertThatCode(() -> new PartTree(repositoryMethod.getName(), ApplicationProgrammeEntryOptionSelection.class))
                .doesNotThrowAnyException();
    }
}
