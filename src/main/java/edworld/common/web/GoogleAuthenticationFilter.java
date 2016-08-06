package edworld.common.web;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import edworld.common.infra.Config;
import edworld.common.infra.util.HTTPUtil;
import edworld.common.infra.util.JSONUtil;
import edworld.common.web.infra.UserInfo;
import edworld.common.web.infra.WebUtil;

public class GoogleAuthenticationFilter implements Filter {
	private static final String PROVIDER_GOOGLE = "google";
	private static String PATH_ERROR = "/error";
	private String googleClientId;
	private String googleSecretKey;
	private String callbackURL;

	public void init(FilterConfig filterConfig) throws ServletException {
		googleClientId = filterConfig.getInitParameter("google-client-id");
		googleSecretKey = filterConfig.getInitParameter("google-secret-key");
		callbackURL = filterConfig.getInitParameter("callback-url");
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		if (callbackURL.endsWith(httpRequest.getRequestURI())) {
			String code = httpRequest.getParameter("code");
			if (code == null) {
				httpResponse.sendRedirect(PATH_ERROR + "?message=" + encodeURL(httpRequest.getParameter("error")));
				return;
			}
			String state = httpRequest.getParameter("state");
			String tokenResponse = googleRequestAccessToken(code, httpRequest);
			if (tokenResponse != null) {
				Map<String, String> map = JSONUtil.toMap(tokenResponse);
				String token = map.get("access_token");
				String tokenType = map.get("token_type");
				String userInfoResponse = HTTPUtil.get("https://www.googleapis.com/userinfo/v2/me", token, tokenType);
				map = JSONUtil.toMap(userInfoResponse);
				if (!"true".equalsIgnoreCase(map.get("verified_email")))
					map.remove("email");
				UserInfo userInfo = createUserInfo(PROVIDER_GOOGLE, map, token, null);
				WebUtil.setUserInfo(userInfo, httpRequest);
				WebUtil.setSessionAttribute("PendingLogin", userInfo, httpRequest);
				httpResponse.sendRedirect(stateToRequest(state));
				return;
			}
			httpResponse.sendRedirect(PATH_ERROR + "?message=" + encodeURL("token not found"));
			return;
		} else if (WebUtil.getUserInfo(httpRequest) != null) {
			chain.doFilter(request, response);
			return;
		}
		httpResponse.sendRedirect(googleRequestAuthorizationCode(httpRequest));
	}

	private UserInfo createUserInfo(String provider, Map<String, String> map, String token, String refreshToken) {
		return new UserInfo(null, map.get("name"), map.get("link"), map.get("picture"), map.get("gender"),
				map.get("locale"), map.get("email"), provider, map.get("id"), token, refreshToken,
				adminUser(map.get("email")), roles(map.get("email")));
	}

	/**
	 * Override this method to verify if a given user is an application
	 * administrator.
	 * 
	 * @param email
	 *            the user's e-Mail
	 * @return true if the given user is an application administrator
	 */
	protected boolean adminUser(String email) {
		return false;
	}

	/**
	 * Override this method to create a list containing the roles of a given
	 * user. Each role corresponds to a set of authorized actions on the
	 * application.
	 * 
	 * @param email
	 *            the user's e-Mail
	 * @return the list containing the roles of the given user
	 */
	protected Collection<String> roles(String email) {
		return Collections.emptyList();
	}

	private String googleRequestAuthorizationCode(HttpServletRequest httpRequest) {
		StringBuilder query = new StringBuilder();
		addParameter(query, "response_type", "code");
		addParameter(query, "client_id", googleClientId);
		addParameter(query, "redirect_uri", callbackURL);
		addParameter(query, "state", requestToState(httpRequest));
		addParameter(query, "scope", "profile email");
		return "https://accounts.google.com/o/oauth2/v2/auth?" + query;
	}

	private String googleRequestAccessToken(String code, HttpServletRequest httpRequest) {
		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		params.add(new BasicNameValuePair("grant_type", "authorization_code"));
		params.add(new BasicNameValuePair("code", code));
		params.add(new BasicNameValuePair("client_id", googleClientId));
		params.add(new BasicNameValuePair("client_secret", googleSecretKey));
		params.add(new BasicNameValuePair("redirect_uri", callbackURL));
		return HTTPUtil.post("https://www.googleapis.com/oauth2/v4/token", params);
	}

	private String requestToState(HttpServletRequest httpRequest) {
		String state = httpRequest.getRequestURI();
		if (httpRequest.getQueryString() != null)
			state += "?" + httpRequest.getQueryString();
		return Base64.encodeBase64String(state.getBytes());
	}

	private String stateToRequest(String state) {
		return new String(Base64.decodeBase64(state));
	}

	private void addParameter(StringBuilder query, String name, String value) {
		if (query.length() > 0)
			query.append('&');
		query.append(name + "=" + encodeURL(value));
	}

	private String encodeURL(String text) {
		try {
			return URLEncoder.encode(text, Config.getEncoding());
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public void destroy() {
	}
}
