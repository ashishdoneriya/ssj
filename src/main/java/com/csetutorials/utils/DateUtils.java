package com.csetutorials.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {

	public static String postWritingDataFormat = "yyyy-MM-dd'T'HH:mm:ssXXX";

	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

	public static synchronized Date parse(String sDate, String pattern) throws ParseException {
		if (StringUtils.isBlank(sDate)) {
			return null;
		}
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		return sdf.parse(sDate.trim());

	}

	public synchronized String format(Date date, String pattern) {
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		return sdf.format(date);
	}

	public synchronized static String getSiteMapString(Date date) {
		if (date == null) {
			return null;
		}
		return sdf.format(date);
	}

}
