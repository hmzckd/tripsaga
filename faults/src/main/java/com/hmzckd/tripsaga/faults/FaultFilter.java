package com.hmzckd.tripsaga.faults;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

class FaultFilter extends OncePerRequestFilter {

	private final Faults faults;

	FaultFilter(Faults faults) {
		this.faults = faults;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !HttpMethod.POST.matches(request.getMethod()) || request.getRequestURI().startsWith("/admin/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		sleep(faults.delayMs());
		if (faults.takeFailure()) {
			unavailable(response, "Injected failure: the request was not processed");
			return;
		}
		if (faults.takeLostAnswer()) {
			ContentCachingResponseWrapper processed = new ContentCachingResponseWrapper(response);
			chain.doFilter(request, processed);
			response.reset();
			unavailable(response, "Injected failure: the request was processed but the answer was lost");
			return;
		}
		chain.doFilter(request, response);
	}

	static void unavailable(HttpServletResponse response, String detail) throws IOException {
		response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		response.setContentType("application/problem+json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"title\":\"Service Unavailable\",\"status\":503,\"detail\":\"" + detail + "\"}");
	}

	private static void sleep(long millis) {
		if (millis <= 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
