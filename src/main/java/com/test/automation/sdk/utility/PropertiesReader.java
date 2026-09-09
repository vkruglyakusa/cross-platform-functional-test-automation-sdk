package com.test.automation.sdk.utility;

import com.test.automation.sdk.config.SdkConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class PropertiesReader {
	public static  File f;
	public static FileInputStream FI;
	public static Properties Prop = new Properties();

	public String getProperty(String propertyName) throws IOException {
		f = new File(SdkConfig.CONFIG_PROPERTIES);
		FI = new FileInputStream(f);
		Prop.load(FI);
		return Prop.getProperty(propertyName);
	}
}
