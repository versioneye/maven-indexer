package com.versioneye.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: robertreiz
 * Date: 8/9/13
 * Time: 3:27 PM
 */
public class PropertiesUtils {

    public Properties readProperties(String filePath) throws Exception{
        Properties properties = new Properties();
        InputStream inputStream = null;
        File file = new File(filePath);
        inputStream = new FileInputStream( file );
        properties.load(inputStream);
        return properties;
    }

}
