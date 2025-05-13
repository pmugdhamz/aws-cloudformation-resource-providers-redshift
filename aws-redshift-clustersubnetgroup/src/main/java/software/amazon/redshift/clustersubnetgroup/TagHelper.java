package software.amazon.redshift.clustersubnetgroup;

import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TagHelper {
    public static Map<String, String> getDesiredTags(ResourceHandlerRequest<ResourceModel> request, ResourceModel desiredResourceState) {
        Map<String, String> desiredTags = new HashMap<>();
        desiredTags.putAll(Optional.ofNullable(Translator.translateFromResourceModelToSdkTags(
                desiredResourceState.getTags())).orElse(Collections.emptyMap()));
        desiredTags.putAll(Optional.ofNullable(request.getDesiredResourceTags())
                .orElse(Collections.emptyMap()));
        desiredTags.putAll(Optional.ofNullable(request.getSystemTags())
                .orElse(Collections.emptyMap()));
        return desiredTags;
    }

    public static Map<String, String> getPreviousTags(ResourceHandlerRequest<ResourceModel> request, ResourceModel previousResourceState) {
        Map<String, String> previousTags = new HashMap<>();
        previousTags.putAll(Optional.ofNullable(Translator.translateFromResourceModelToSdkTags(
                        previousResourceState != null ? previousResourceState.getTags() : null))
                .orElse(Collections.emptyMap()));
        previousTags.putAll(Optional.ofNullable(request.getPreviousResourceTags())
                .orElse(Collections.emptyMap()));
        return previousTags;
    }
}
