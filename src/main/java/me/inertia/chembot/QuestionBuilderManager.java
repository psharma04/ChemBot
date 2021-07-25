package me.inertia.chembot;

import java.io.IOException;
import java.io.InputStream;

public class QuestionBuilderManager {
    public static void startBuilder(){
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec("java -jar data/questions/QuestionBuilder.jar");
        } catch (IOException e) {
            e.printStackTrace();
        }
        InputStream in = proc.getInputStream();
        InputStream err = proc.getErrorStream();
    }
}
