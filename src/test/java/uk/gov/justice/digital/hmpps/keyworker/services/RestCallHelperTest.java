package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelper.queryParamsOf;

public class RestCallHelperTest {

    @Test
    public void queryParamsOf_none_returnsEmptyMap() {
        final var queryParams = queryParamsOf();

        assertThat(queryParams).isEmpty();
    }

    @Test
    public void queryParamsOf_singlePair_returnedInMap() {
        final var queryParams = queryParamsOf("someKey", "someValue");

        assertThat(queryParams).containsEntry("someKey", List.of("someValue"));
    }

    @Test
    public void queryParamsOf_severalPairs_returnedInMap() {
        final var queryParams = queryParamsOf("someKey", "someValue", "someKey2", "someValue2", "someKey3", "someValue3");
        final MultiValueMap<String, String> expected = new LinkedMultiValueMap<>();
        expected.add("someKey", "someValue");
        expected.add("someKey2", "someValue2");
        expected.add("someKey3", "someValue3");

        assertThat(queryParams).containsExactlyEntriesOf(expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void queryParamsOf_notInPairs_throwsEx() {
        queryParamsOf("single");
    }
}
