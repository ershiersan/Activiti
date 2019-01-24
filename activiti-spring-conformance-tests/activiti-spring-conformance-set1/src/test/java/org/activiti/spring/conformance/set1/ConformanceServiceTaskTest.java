package org.activiti.spring.conformance.set1;

import static org.activiti.spring.conformance.set1.Set1RuntimeTestConfiguration.collectedEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.activiti.api.model.shared.event.RuntimeEvent;
import org.activiti.api.process.model.IntegrationContext;
import org.activiti.api.process.model.ProcessInstance;
import org.activiti.api.process.model.builders.ProcessPayloadBuilder;
import org.activiti.api.process.model.events.BPMNActivityEvent;
import org.activiti.api.process.model.events.BPMNSequenceFlowTakenEvent;
import org.activiti.api.process.model.events.ProcessRuntimeEvent;
import org.activiti.api.process.runtime.ProcessRuntime;
import org.activiti.api.runtime.shared.NotFoundException;
import org.activiti.spring.conformance.util.security.SecurityUtil;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ConformanceServiceTaskTest {

    private static final String MY_BUSINESS_KEY = "my-business-key";
    private final String processKey = "servicetas-820b2020-968d-4d34-bac4-5769192674f2";
    @Autowired
    private ProcessRuntime processRuntime;

    @Autowired
    private SecurityUtil securityUtil;

    @After
    public void cleanUp() {
        Set1RuntimeTestConfiguration.reset(); 
    }

    /*
     * This test covers the ServiceTask with Implementation.bpmn20.xml process
     * This execution should generate 11 events:
     *   - PROCESS_CREATED
     *   - PROCESS_STARTED,
     *   - ACTIVITY_STARTED,
     *   - ACTIVITY_COMPLETED,
     *   - SEQUENCE_FLOW_TAKEN,
     *   - ACTIVITY_STARTED,
     *   - ACTIVITY_COMPLETED,
     *   - SEQUENCE_FLOW_TAKEN,
     *   - ACTIVITY_STARTED,
     *   - ACTIVITY_COMPLETED,
     *   - PROCESS_COMPLETED
     *  And the Process Instance Status should be Completed
     *  Connectors are executed in a Sync fashion, so the logic will be exexuted and the BPMN Activity completed automatically.
     *  IntegrationContext attributes shall capture and contain valid execution context of the underlying process instance. 
     *  No further operation can be executed on the process due the fact that it start and finish in the same transaction
     */
    @Test
    public void shouldBeAbleToStartProcess() {
        securityUtil.logInAs("user1");
        //when
        ProcessInstance processInstance = processRuntime.start(ProcessPayloadBuilder
                .start()
                .withProcessDefinitionKey(processKey)
                .withBusinessKey(MY_BUSINESS_KEY)
                .withName("my-process-instance-name")
                .build());

        //then
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.getStatus()).isEqualTo(ProcessInstance.ProcessInstanceStatus.COMPLETED);
        assertThat(processInstance.getBusinessKey()).isEqualTo(MY_BUSINESS_KEY);
        assertThat(processInstance.getName()).isEqualTo("my-process-instance-name");

        // No Process Instance should be found
        Throwable throwable = catchThrowable(() -> processRuntime.processInstance(processInstance.getId()));

        assertThat(throwable)
                .isInstanceOf(NotFoundException.class);

        // No Variable Instance should be found
        throwable = catchThrowable(() -> processRuntime.variables(
                ProcessPayloadBuilder
                        .variables()
                        .withProcessInstanceId(processInstance.getId())
                        .build()));
        assertThat(throwable)
                .isInstanceOf(NotFoundException.class);

        assertThat(Set1RuntimeTestConfiguration.isConnector1Executed()).isTrue();

        // and then
        IntegrationContext integrationContext = Set1RuntimeTestConfiguration.getResultIntegrationContext();

        assertThat(integrationContext).isNotNull();
        assertThat(integrationContext.getBusinessKey()).isEqualTo(processInstance.getBusinessKey());
        assertThat(integrationContext.getProcessDefinitionId()).isEqualTo(processInstance.getProcessDefinitionId());
        assertThat(integrationContext.getProcessInstanceId()).isEqualTo(processInstance.getId());
        assertThat(integrationContext.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
        assertThat(integrationContext.getProcessDefinitionVersion()).isEqualTo(1);
        assertThat(integrationContext.getParentProcessInstanceId()).isNull();
        
        // and then
        assertThat(collectedEvents)
                .extracting(RuntimeEvent::getEventType)
                .containsExactly(
                        ProcessRuntimeEvent.ProcessEvents.PROCESS_CREATED,
                        ProcessRuntimeEvent.ProcessEvents.PROCESS_STARTED,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_STARTED,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_COMPLETED,
                        BPMNSequenceFlowTakenEvent.SequenceFlowEvents.SEQUENCE_FLOW_TAKEN,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_STARTED,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_COMPLETED,
                        BPMNSequenceFlowTakenEvent.SequenceFlowEvents.SEQUENCE_FLOW_TAKEN,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_STARTED,
                        BPMNActivityEvent.ActivityEvents.ACTIVITY_COMPLETED,
                        ProcessRuntimeEvent.ProcessEvents.PROCESS_COMPLETED);

    }



}
