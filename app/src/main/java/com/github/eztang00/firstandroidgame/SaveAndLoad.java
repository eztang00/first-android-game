package com.github.eztang00.firstandroidgame;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.util.Arrays;

public class SaveAndLoad {

    public static <T> T gsonLoadRawResource(Context context, int id, Type type) {
        try (InputStream is = context.getResources().openRawResource(id);
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr)
        ) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString();
            Gson gson = new Gson(); // Or use new GsonBuilder().create();
            return gson.fromJson(json, type); // deserializes json into target2
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // this failed because somehow gson cannot load ObservableArrayMap
    // and probably will face other problems in the future
    public static GameProgress gsonLoad(Context context) {
        if (Arrays.asList(context.fileList()).contains("progress.json")) {
            //copied this from https://www.w3docs.com/snippets/java/read-write-string-from-to-a-file-in-android.html
            try (FileInputStream fis = context.openFileInput("progress.json");
                 InputStreamReader isr = new InputStreamReader(fis);
                 BufferedReader br = new BufferedReader(isr)
            ) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                String json = sb.toString();
                Gson gson = new Gson(); // Or use new GsonBuilder().create();
                return new GameProgress(gson.fromJson(json, SerializableGameProgress.class)); // deserializes json into target2
            } catch (IOException | JsonSyntaxException e) {
//                throw new RuntimeException(e);
                e.printStackTrace();
                return new GameProgress();
                // still return because maybe happens now and then due to game update or something?
            }
        } else {
            return new GameProgress();
        }
    }
    //causes weird compile error despite using gradle "implementation 'com.sun.xml.bind:jaxb-impl:2.3.3'"
//    public static GameProgress jaxbLoad(Context context) {
//        if (Arrays.asList(context.fileList()).contains("progress.xml")) {
//
//            //copied from https://howtodoinjava.com/jaxb/write-object-to-xml/
//            File xmlFile = new File(context.getFilesDir(), "progress.xml");
//
//            JAXBContext jaxbContext;
//            try
//            {
//                jaxbContext = JAXBContext.newInstance(GameProgress.class);
//                Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
//                GameProgress progress = (GameProgress) jaxbUnmarshaller.unmarshal(xmlFile);
//
//                return progress;
//            }
//            catch (JAXBException e)
//            {
//                throw new RuntimeException(e);
//            }
//        } else {
//            return new GameProgress();
//        }
//    }
    // this failed for loading ObservableArrayMap, at first it was errors due it not
    // implementing Serializable, then tried subclassing it Serializable but the loaded
    // object still failed to actually contain anything
    public static GameProgress objectStreamLoad(Context context) {
        if (Arrays.asList(context.fileList()).contains("progress.ser")) {
            try (FileInputStream fis = context.openFileInput("progress.ser");
                 ObjectInputStream ois = new ObjectInputStream(fis);
            ) {
                return new GameProgress ((SerializableGameProgress) ois.readObject());
            } catch (IOException | ClassNotFoundException e) {
//                throw new RuntimeException(e);
                e.printStackTrace();
                return new GameProgress();
                // still return because maybe happens now and then due to game update or something?
            }
        } else {
            return new GameProgress();
        }

    }

    public static void gsonSave(GameProgress progress, Context context) {
        Gson gson = new Gson(); // Or use new GsonBuilder().create();
        String json = gson.toJson(new SerializableGameProgress(progress)); // serializes target to JSON
        try (FileOutputStream fos = context.openFileOutput("progress.json", Context.MODE_PRIVATE)) {
            fos.write(json.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    //causes weird compile error despite using gradle "implementation 'com.sun.xml.bind:jaxb-impl:2.3.3'"
//    public static void jaxbSave(GameProgress progress, Context context) {
//        //from https://howtodoinjava.com/jaxb/write-object-to-xml/
//        try
//        {
//            //Create JAXB Context
//            JAXBContext jaxbContext = JAXBContext.newInstance(GameProgress.class);
//
//            //Create Marshaller
//            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
//
//            //Required formatting??
//            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
//
//            //Store XML to File
//            File file = new File(context.getFilesDir(), "progress.xml");
//
//            //Writes XML file to file-system
//            jaxbMarshaller.marshal(progress, file);
//        } catch (JAXBException e) {
//            throw new RuntimeException(e);
//        }
//    }
    public static void objectStreamSave(GameProgress progress, Context context) {
        try (FileOutputStream fos = context.openFileOutput("progress.ser", Context.MODE_PRIVATE);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(new SerializableGameProgress(progress));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
