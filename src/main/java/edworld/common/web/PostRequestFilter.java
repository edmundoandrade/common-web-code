package edworld.common.web;

import static org.jboss.resteasy.plugins.providers.multipart.InputPart.DEFAULT_CONTENT_TYPE_PROPERTY;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import edworld.common.infra.Config;

public class PostRequestFilter implements Filter {
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		request.setAttribute(DEFAULT_CONTENT_TYPE_PROPERTY, Service.PLAIN);
		if (request.getCharacterEncoding() == null)
			request.setCharacterEncoding(Config.getEncoding());
		chain.doFilter(request, response);
	}

	public void destroy() {
	}
}
