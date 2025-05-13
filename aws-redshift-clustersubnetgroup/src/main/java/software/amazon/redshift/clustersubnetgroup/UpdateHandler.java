package software.amazon.redshift.clustersubnetgroup;

import software.amazon.awssdk.services.redshift.RedshiftClient;
import software.amazon.awssdk.services.redshift.model.ClusterSubnetGroupNotFoundException;
import software.amazon.awssdk.services.redshift.model.ClusterSubnetQuotaExceededException;
import software.amazon.awssdk.services.redshift.model.InvalidSubnetException;
import software.amazon.awssdk.services.redshift.model.InvalidTagException;
import software.amazon.awssdk.services.redshift.model.ModifyClusterSubnetGroupRequest;
import software.amazon.awssdk.services.redshift.model.ModifyClusterSubnetGroupResponse;
import software.amazon.awssdk.services.redshift.model.UnauthorizedOperationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;
import java.util.Map;

public class UpdateHandler extends BaseHandlerStd {

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<RedshiftClient> proxyClient,
            final Logger logger) {

        this.logger = logger;
        final ResourceModel desiredResourceState = request.getDesiredResourceState();
        final ResourceModel previousResourceState = request.getPreviousResourceState();

        // Get resource ARN
        final String resourceName = String.format("arn:%s:redshift:%s:%s:subnetgroup:%s",
                request.getAwsPartition(),
                request.getRegion(),
                request.getAwsAccountId(),
                desiredResourceState.getClusterSubnetGroupName());

        // Handle desired tags (resource tags + stack tags + system tags)
        Map<String, String> allDesiredTags = TagHelper.getDesiredTags(request, desiredResourceState);
        Map<String, String> allPreviousTags = TagHelper.getPreviousTags(request, previousResourceState);

        List<Tag> desiredTags = Translator.translateTagsMapToTagCollection(allDesiredTags);
        List<Tag> previousTags = Translator.translateTagsMapToTagCollection(allPreviousTags);

        return ProgressEvent.progress(desiredResourceState, callbackContext)
                // Update subnet group
                .then(progress -> updateSubnetGroup(progress, proxy, proxyClient))
                // Update tags
                .then(progress -> updateTags(progress, proxy, proxyClient, resourceName, desiredTags, previousTags))
                // Read final state
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateSubnetGroup(
            ProgressEvent<ResourceModel, CallbackContext> progress,
            AmazonWebServicesClientProxy proxy,
            ProxyClient<RedshiftClient> proxyClient) {
        return proxy.initiate("AWS-Redshift-ClusterSubnetGroup::Update::SubnetGroup", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .makeServiceCall(this::modifyClusterSubnetGroup)
                .handleError(this::handleError)
                .progress();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateTags(
            ProgressEvent<ResourceModel, CallbackContext> progress,
            AmazonWebServicesClientProxy proxy,
            ProxyClient<RedshiftClient> proxyClient,
            String resourceName,
            List<Tag> desiredTags,
            List<Tag> previousTags) {

        ModifyTagsRequest modifyTagsRequest = Translator.translateToUpdateTagsRequest(desiredTags, previousTags, resourceName);

        if ((modifyTagsRequest.getCreateNewTagsRequest() == null || modifyTagsRequest.getCreateNewTagsRequest().tags().isEmpty()) &&
                (modifyTagsRequest.getDeleteOldTagsRequest() == null || modifyTagsRequest.getDeleteOldTagsRequest().tagKeys().isEmpty())) {
            return progress;
        }

        return proxy.initiate("AWS-Redshift-ClusterSubnetGroup::Update::UpdateTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> modifyTagsRequest)
                .makeServiceCall((req, client) -> {
                    if (req.getDeleteOldTagsRequest() != null && !req.getDeleteOldTagsRequest().tagKeys().isEmpty()) {
                        client.injectCredentialsAndInvokeV2(req.getDeleteOldTagsRequest(), client.client()::deleteTags);
                    }
                    if (req.getCreateNewTagsRequest() != null && !req.getCreateNewTagsRequest().tags().isEmpty()) {
                        client.injectCredentialsAndInvokeV2(req.getCreateNewTagsRequest(), client.client()::createTags);
                    }
                    return req;
                })
                .handleError(this::handleTagError)
                .progress();
    }

    private ModifyClusterSubnetGroupResponse modifyClusterSubnetGroup(
            final ModifyClusterSubnetGroupRequest awsRequest,
            final ProxyClient<RedshiftClient> proxyClient) {
        ModifyClusterSubnetGroupResponse response = proxyClient.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::modifyClusterSubnetGroup);
        logger.log(String.format("%s has successfully been updated.", ResourceModel.TYPE_NAME));
        return response;
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleError(
            final ModifyClusterSubnetGroupRequest awsRequest,
            final Exception exception,
            final ProxyClient<RedshiftClient> client,
            final ResourceModel model,
            final CallbackContext context) {

        if (exception instanceof ClusterSubnetGroupNotFoundException) {
            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
        } else if (exception instanceof ClusterSubnetQuotaExceededException) {
            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.ServiceLimitExceeded);
        } else if (exception instanceof InvalidSubnetException) {
            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InvalidRequest);
        } else if (exception instanceof UnauthorizedOperationException) {
            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.AccessDenied);
        }
        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleTagError(
            final ModifyTagsRequest awsRequest,
            final Exception exception,
            final ProxyClient<RedshiftClient> client,
            final ResourceModel model,
            final CallbackContext context) {

        if (exception instanceof InvalidTagException) {
            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InvalidRequest);
        }
        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
    }
}
