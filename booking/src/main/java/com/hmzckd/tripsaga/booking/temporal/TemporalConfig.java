package com.hmzckd.tripsaga.booking.temporal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hmzckd.tripsaga.booking.saga.BookingWorkflow;
import com.hmzckd.tripsaga.booking.saga.BookingWorkflowImpl;
import com.hmzckd.tripsaga.booking.saga.FlightActivities;
import com.hmzckd.tripsaga.booking.saga.HotelActivities;
import com.hmzckd.tripsaga.booking.saga.PaymentActivities;
import com.hmzckd.tripsaga.booking.saga.RecordActivities;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

@Configuration(proxyBeanMethods = false)
class TemporalConfig {

	@Bean(destroyMethod = "shutdown")
	WorkflowServiceStubs workflowService(TemporalProperties properties) {
		return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
				.setTarget(properties.target())
				.build());
	}

	@Bean
	WorkflowClient workflowClient(WorkflowServiceStubs workflowService, TemporalProperties properties) {
		return WorkflowClient.newInstance(workflowService, WorkflowClientOptions.newBuilder()
				.setNamespace(properties.namespace())
				.build());
	}

	@Bean
	WorkerFactory workerFactory(WorkflowClient client, FlightActivities flight, HotelActivities hotel,
			PaymentActivities payment, RecordActivities records) {
		WorkerFactory factory = WorkerFactory.newInstance(client);
		Worker worker = factory.newWorker(BookingWorkflow.TASK_QUEUE);
		worker.registerWorkflowImplementationTypes(BookingWorkflowImpl.class);
		worker.registerActivitiesImplementations(flight, hotel, payment, records);
		return factory;
	}

	@Bean
	WorkerLifecycle workerLifecycle(WorkerFactory factory) {
		return new WorkerLifecycle(factory);
	}
}
