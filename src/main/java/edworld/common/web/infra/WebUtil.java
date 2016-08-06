package edworld.common.web.infra;

import javax.servlet.http.HttpServletRequest;

public class WebUtil {
	private static final String ATTRIBUTE_USER_INFO = "userInfo";

	public static Object getSessionAttribute(String name, HttpServletRequest httpRequest) {
		return httpRequest.getSession().getAttribute(name);
	}

	public static void setSessionAttribute(String name, Object value, HttpServletRequest httpRequest) {
		httpRequest.getSession().setAttribute(name, value);
	}

	public static UserInfo getUserInfo(HttpServletRequest httpRequest) {
		return (UserInfo) getSessionAttribute(ATTRIBUTE_USER_INFO, httpRequest);
	}

	public static void setUserInfo(UserInfo userInfo, HttpServletRequest httpRequest) {
		setSessionAttribute(ATTRIBUTE_USER_INFO, userInfo, httpRequest);
	}
}
