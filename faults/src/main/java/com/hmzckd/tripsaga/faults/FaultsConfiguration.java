package com.hmzckd.tripsaga.faults;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("tripsaga.faults.enabled")
public class FaultsConfiguration {

	@Bean
	Faults faults() {
		return new Faults();
	}

	@Bean
	FaultsController faultsController(Faults faults) {
		return new FaultsController(faults);
	}

	@Bean
	FilterRegistrationBean<FaultFilter> faultFilter(Faults faults) {
		FilterRegistrationBean<FaultFilter> registration = new FilterRegistrationBean<>(new FaultFilter(faults));
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
