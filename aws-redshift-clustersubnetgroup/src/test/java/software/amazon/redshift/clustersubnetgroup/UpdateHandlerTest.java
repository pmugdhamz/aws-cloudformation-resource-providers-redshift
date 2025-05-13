package software.amazon.redshift.clustersubnetgroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.redshift.RedshiftClient;
import software.amazon.awssdk.services.redshift.model.ClusterSubnetGroupNotFoundException;
import software.amazon.awssdk.services.redshift.model.CreateTagsRequest;
import software.amazon.awssdk.services.redshift.model.CreateTagsResponse;
import software.amazon.awssdk.services.redshift.model.DeleteTagsRequest;
import software.amazon.awssdk.services.redshift.model.DeleteTagsResponse;
import software.amazon.awssdk.services.redshift.model.DescribeClusterSubnetGroupsRequest;
import software.amazon.awssdk.services.redshift.model.DescribeClusterSubnetGroupsResponse;
import software.amazon.awssdk.services.redshift.model.DescribeTagsRequest;
import software.amazon.awssdk.services.redshift.model.DescribeTagsResponse;
import software.amazon.awssdk.services.redshift.model.ModifyClusterSubnetGroupRequest;
import software.amazon.awssdk.services.redshift.model.ModifyClusterSubnetGroupResponse;
import software.amazon.awssdk.services.redshift.model.Subnet;
import software.amazon.awssdk.services.redshift.model.TaggedResource;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.redshift.clustersubnetgroup.TestUtils.AWS_ACCOUNT_ID;
import static software.amazon.redshift.clustersubnetgroup.TestUtils.AWS_PARTITION;
import static software.amazon.redshift.clustersubnetgroup.TestUtils.AWS_REGION;
import static software.amazon.redshift.clustersubnetgroup.TestUtils.BASIC_MODEL;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<RedshiftClient> proxyClient;

    @Mock
    RedshiftClient sdkClient;

    private UpdateHandler handler;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(RedshiftClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
        handler = new UpdateHandler();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel desiredModel = ResourceModel.builder()
                .clusterSubnetGroupName("test-group")
                .description("Updated description")
                .subnetIds(Arrays.asList("subnet-12345", "subnet-67890"))
                .tags(Collections.singletonList(Tag.builder().key("testKey").value("testValue").build()))
                .build();


        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .previousResourceState(BASIC_MODEL)
                .desiredResourceTags(Collections.singletonMap("testKey", "testValue"))
                .previousResourceTags(Collections.emptyMap())
                .systemTags(Collections.emptyMap())
                .awsAccountId(AWS_ACCOUNT_ID)
                .region(AWS_REGION)
                .awsPartition(AWS_PARTITION)
                .build();

        when(proxyClient.client().modifyClusterSubnetGroup(any(ModifyClusterSubnetGroupRequest.class)))
                .thenReturn(ModifyClusterSubnetGroupResponse.builder()
                        .clusterSubnetGroup(software.amazon.awssdk.services.redshift.model.ClusterSubnetGroup.builder()
                                .clusterSubnetGroupName("test-group")
                                .description("Updated description")
                                .subnets(Arrays.asList(
                                        Subnet.builder().subnetIdentifier("subnet-12345").build(),
                                        Subnet.builder().subnetIdentifier("subnet-67890").build()))
                                .tags(Collections.singletonList(
                                        software.amazon.awssdk.services.redshift.model.Tag.builder()
                                                .key("testKey")
                                                .value("testValue")
                                                .build()))
                                .build())
                        .build());

        when(proxyClient.client().describeClusterSubnetGroups(any(DescribeClusterSubnetGroupsRequest.class)))
                .thenReturn(DescribeClusterSubnetGroupsResponse.builder()
                        .clusterSubnetGroups(software.amazon.awssdk.services.redshift.model.ClusterSubnetGroup.builder()
                                .clusterSubnetGroupName("test-group")
                                .description("Updated description")
                                .subnets(Arrays.asList(
                                        Subnet.builder().subnetIdentifier("subnet-12345").build(),
                                        Subnet.builder().subnetIdentifier("subnet-67890").build()))
                                .tags(Collections.singletonList(
                                        software.amazon.awssdk.services.redshift.model.Tag.builder()
                                                .key("testKey")
                                                .value("testValue")
                                                .build()))
                                .build())
                        .build());

        when(proxyClient.client().describeTags(any(DescribeTagsRequest.class)))
                .thenReturn(DescribeTagsResponse.builder()
                        .taggedResources(TaggedResource.builder()
                                .tag(software.amazon.awssdk.services.redshift.model.Tag.builder()
                                        .key("testKey")
                                        .value("testValue")
                                        .build())
                                .build())
                        .build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus())
                .withFailMessage("Expected SUCCESS but was " + response.getStatus() + ". Error: " + response.getMessage())
                .isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_UpdateTags() {
        final ResourceModel desiredModel = ResourceModel.builder()
                .clusterSubnetGroupName("test-group")
                .description("Test description")
                .subnetIds(Arrays.asList("subnet-12345", "subnet-67890"))
                .tags(Collections.singletonList(Tag.builder().key("newKey").value("newValue").build()))
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .previousResourceState(BASIC_MODEL)
                .desiredResourceTags(Collections.singletonMap("newKey", "newValue"))
                .previousResourceTags(Collections.singletonMap("oldKey", "oldValue"))
                .systemTags(Collections.emptyMap())
                .awsAccountId(AWS_ACCOUNT_ID)
                .region(AWS_REGION)
                .awsPartition(AWS_PARTITION)
                .build();

        when(proxyClient.client().modifyClusterSubnetGroup(any(ModifyClusterSubnetGroupRequest.class)))
                .thenReturn(ModifyClusterSubnetGroupResponse.builder()
                        .clusterSubnetGroup(software.amazon.awssdk.services.redshift.model.ClusterSubnetGroup.builder()
                                .clusterSubnetGroupName("test-group")
                                .description("Test description")
                                .subnets(Arrays.asList(
                                        Subnet.builder().subnetIdentifier("subnet-12345").build(),
                                        Subnet.builder().subnetIdentifier("subnet-67890").build()))
                                .tags(Collections.singletonList(
                                        software.amazon.awssdk.services.redshift.model.Tag.builder()
                                                .key("newKey")
                                                .value("newValue")
                                                .build()))
                                .build())
                        .build());

        when(proxyClient.client().createTags(any(CreateTagsRequest.class)))
                .thenReturn(CreateTagsResponse.builder().build());

        when(proxyClient.client().deleteTags(any(DeleteTagsRequest.class)))
                .thenReturn(DeleteTagsResponse.builder().build());

        when(proxyClient.client().describeClusterSubnetGroups(any(DescribeClusterSubnetGroupsRequest.class)))
                .thenReturn(DescribeClusterSubnetGroupsResponse.builder()
                        .clusterSubnetGroups(software.amazon.awssdk.services.redshift.model.ClusterSubnetGroup.builder()
                                .clusterSubnetGroupName("test-group")
                                .description("Test description")
                                .subnets(Arrays.asList(
                                        Subnet.builder().subnetIdentifier("subnet-12345").build(),
                                        Subnet.builder().subnetIdentifier("subnet-67890").build()))
                                .tags(Collections.singletonList(
                                        software.amazon.awssdk.services.redshift.model.Tag.builder()
                                                .key("newKey")
                                                .value("newValue")
                                                .build()))
                                .build())
                        .build());

        when(proxyClient.client().describeTags(any(DescribeTagsRequest.class)))
                .thenReturn(DescribeTagsResponse.builder()
                        .taggedResources(TaggedResource.builder()
                                .tag(software.amazon.awssdk.services.redshift.model.Tag.builder()
                                        .key("newKey")
                                        .value("newValue")
                                        .build())
                                .build())
                        .build());

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus())
                .withFailMessage("Expected SUCCESS but was " + response.getStatus() + ". Error: " + response.getMessage())
                .isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).createTags(any(CreateTagsRequest.class));
        verify(proxyClient.client()).deleteTags(any(DeleteTagsRequest.class));
    }

    @Test
    public void handleRequest_NotFound() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(BASIC_MODEL)
                .build();

        when(proxyClient.client().modifyClusterSubnetGroup(any(ModifyClusterSubnetGroupRequest.class)))
                .thenThrow(ClusterSubnetGroupNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }
}
