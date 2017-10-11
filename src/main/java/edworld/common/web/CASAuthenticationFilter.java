package edworld.common.web;

import java.util.Optional;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.jasig.cas.client.authentication.AuthenticationFilter;
import org.jasig.cas.client.configuration.ConfigurationKeys;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * Authentication via CAS with support to extra path in the application URL.
 * 
 * It's considered an extra path the portion of the parameter 'serverName' that
 * follows 'protocol://host:port'.
 */
public class CASAuthenticationFilter extends AuthenticationFilter {
	static {
		try {
			ClassPool pool = ClassPool.getDefault();
			pool.insertClassPath(new ClassClassPath(CASAuthenticationFilter.class));
			CtClass customClass = pool.getCtClass("org.jasig.cas.client.util.CommonUtils");
			if (!customClass.isFrozen()) {
				CtMethod method = customClass.getDeclaredMethod("constructServiceUrl",
						new CtClass[] { pool.get("javax.servlet.http.HttpServletRequest"),
								pool.get("javax.servlet.http.HttpServletResponse"), pool.get("java.lang.String"),
								pool.get("java.lang.String"), pool.get("java.lang.String"),
								pool.get("java.lang.String"), pool.get("boolean") });
				method.insertAfter("return edworld.common.web.CASAuthenticationFilter.transformServiceURL($_);");
				customClass.toClass();
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static Optional<String> serverName;
	private static Optional<String> extraPath;

	public static String transformServiceURL(String serviceURL) {
		if (extraPath.isPresent() && !serviceURL.contains(extraPath.get()))
			return serverName.get() + "/" + detectExtraPath(serviceURL).get();
		return serviceURL;
	}

	public static Optional<String> detectExtraPath(String url) {
		String[] urlParts = url.replaceFirst("//", "||").split("/", 2);
		return urlParts.length < 2 || urlParts[1].isEmpty() ? Optional.empty() : Optional.of(urlParts[1]);
	}

	@Override
	protected void initInternal(final FilterConfig filterConfig) throws ServletException {
		super.initInternal(filterConfig);
		serverName = Optional.of(getString(ConfigurationKeys.SERVER_NAME));
		extraPath = detectExtraPath(serverName.get());
	}
}
