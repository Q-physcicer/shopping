package com.shop.common.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public final class CookieUtil {

	static final Logger logger = LoggerFactory.getLogger(CookieUtil.class);

	/**
	 * 得到Cookie的值, 不编码
	 *
	 * @param request
	 * @param cookieName
	 * @return
	 */
	public static String getCookieValue(HttpServletRequest request, String cookieName) {
		return getCookieValue(request, cookieName, false);
	}

	/**
	 * 得到Cookie的值,
	 *
	 * @param request
	 * @param cookieName
	 * @return
	 */
	public static String getCookieValue(HttpServletRequest request, String cookieName, boolean isDecoder) {
		Cookie[] cookieList = request.getCookies();
		if (cookieList == null || cookieName == null){
			return null;
		}
		String retValue = null;
		try {
			for (int i = 0; i < cookieList.length; i++) {
				if (cookieList[i].getName().equals(cookieName)) {
					if (isDecoder) {
						retValue = URLDecoder.decode(cookieList[i].getValue(), "UTF-8");
					} else {
						retValue = cookieList[i].getValue();
					}
					break;
				}
			}
		} catch (UnsupportedEncodingException e) {
			logger.error("Cookie Decode Error.", e);
		}
		return retValue;
	}

	/**
	 * 得到Cookie的值,
	 *
	 * @param request
	 * @param cookieName
	 * @return
	 */
	public static String getCookieValue(HttpServletRequest request, String cookieName, String encodeString) {
		Cookie[] cookieList = request.getCookies();
		if (cookieList == null || cookieName == null){
			return null;
		}
		String retValue = null;
		try {
			for (int i = 0; i < cookieList.length; i++) {
				if (cookieList[i].getName().equals(cookieName)) {
					retValue = URLDecoder.decode(cookieList[i].getValue(), encodeString);
					break;
				}
			}
		} catch (UnsupportedEncodingException e) {
			logger.error("Cookie Decode Error.", e);
		}
		return retValue;
	}

	/**
	 * 生成cookie，并指定编码
	 * @param request 请求
	 * @param response 响应
	 * @param cookieName name
	 * @param cookieValue value
	 * @param encodeString 编码
	 */
	public static final void setCookie(HttpServletRequest request, HttpServletResponse response, String cookieName, String cookieValue, String encodeString) {
		setCookie(request,response,cookieName,cookieValue,null,encodeString, null);
	}

	/**
	 * 生成cookie，并指定生存时间
	 * @param request 请求
	 * @param response 响应
	 * @param cookieName name
	 * @param cookieValue value
	 * @param cookieMaxAge 生存时间
	 */
	public static final void setCookie(HttpServletRequest request, HttpServletResponse response, String cookieName, String cookieValue, Integer cookieMaxAge) {
		setCookie(request,response,cookieName,cookieValue,cookieMaxAge,null, null);
	}

	/**
	 * 设置cookie，不指定httpOnly属性
	 */
	public static final void setCookie(HttpServletRequest request, HttpServletResponse response, String cookieName, String cookieValue, Integer cookieMaxAge, String encodeString) {
		setCookie(request,response,cookieName,cookieValue,cookieMaxAge,encodeString, null);
	}

	/**
	 * 设置Cookie的值，并使其在指定时间内生效
	 *
	 * @param cookieMaxAge
	 *            cookie生效的最大秒数
	 */
	public static final void setCookie(HttpServletRequest request, HttpServletResponse response, String cookieName, String cookieValue, Integer cookieMaxAge, String encodeString, Boolean httpOnly) {
		try {
			if(StringUtils.isBlank(encodeString)) {
				encodeString = "utf-8";
			}

			if (cookieValue == null) {
				cookieValue = "";
			} else {
				cookieValue = URLEncoder.encode(cookieValue, encodeString);
			}
			Cookie cookie = new Cookie(cookieName, cookieValue);
			if (cookieMaxAge != null && cookieMaxAge > 0)
				cookie.setMaxAge(cookieMaxAge);
			// host-only cookie（显式 setDomain 会被 localhost/127.0.0.1 场景的 Cookie 规范拒绝导致丢登录态）
			cookie.setPath("/");

			if(httpOnly != null) {
				cookie.setHttpOnly(httpOnly);
			}
			response.addCookie(cookie);
		} catch (Exception e) {
			logger.error("Cookie Encode Error.", e);
		}
	}

	/** 删除 cookie（修复历史 bug：原实现未 addCookie 到 response，删除不生效） */
	public static final void delCookie(HttpServletRequest request, HttpServletResponse response, String cookieName) {
		Cookie c = new Cookie(cookieName, null);
		c.setPath("/");
		c.setMaxAge(0);  // 生命周期设置为0
		response.addCookie(c);
	}

	/** 兼容老调用（request, cookieName）：无法真正下发删除指令，仅保留编译兼容 */
	public static final void delCookie(HttpServletRequest request, String cookieName) {
		delCookie(request, null, cookieName);
	}

}
