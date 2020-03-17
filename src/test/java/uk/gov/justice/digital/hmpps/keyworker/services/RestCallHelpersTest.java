package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelpersKt.queryParamsOf;
import static uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelpersKt.uriVariablesOf;

public class RestCallHelpersTest {

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

    @Test
    public void uriVariablesOf_none_returnsEmptyMap() {
        final var uriVariables = uriVariablesOf();

        assertThat(uriVariables).isEmpty();
    }

    @Test
    public void uriVariablesOf_singlePair_returnedInMap() {
        final var uriVariables = uriVariablesOf("someKey", "someValue");

        assertThat(uriVariables).containsEntry("someKey", "someValue");
    }

    @Test
    public void uriVariablesOf_severalPairs_returnedInMap() {
        final var uriVariables = uriVariablesOf("someKey", "someValue", "someKey2", "someValue2", "someKey3", "someValue3");
        final Map<String, String> expected = new HashMap<>();
        expected.put("someKey", "someValue");
        expected.put("someKey2", "someValue2");
        expected.put("someKey3", "someValue3");

        assertThat(uriVariables).containsAllEntriesOf(expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void uriVariablesOf_notInPairs_throwsEx() {
        uriVariablesOf("single");
    }

}
