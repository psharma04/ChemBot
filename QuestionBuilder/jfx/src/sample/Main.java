package sample;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.applet.Applet;
import java.awt.*;
import org.json.*;
import org.omg.CORBA.Environment;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class Main extends Application  {
    public static int dirNum = 0;
    @Override
    public void start(Stage primaryStage) throws Exception {
        //String envPath = System.getProperties().getProperty("user.dir");
        //System.setProperty("user.dir",envPath+"\\data\\questions");
        //System.out.println(envPath);

       // envPath = System.getProperties().getProperty("user.dir");
        primaryStage.setTitle("Question Builder");
        primaryStage.setMaxHeight(500);
        primaryStage.setHeight(500);
        Label question = new Label("Question:");
        javafx.scene.control.TextArea questionField = new javafx.scene.control.TextArea();
        questionField.setPrefHeight(100);
        questionField.setPrefWidth(10000);
        question.setMinWidth(70);
        HBox questionBox = new HBox(question, questionField);
        questionBox.setSpacing(10);
        long questionsAmt = Files.find(
                Paths.get("data/questions"),
                1,  // how deep do we want to descend
                (path, attributes) -> attributes.isDirectory()
        ).count() - 1;
        dirNum = (int)questionsAmt;
        ChoiceBox typeSelect = new ChoiceBox();
        typeSelect.getItems().add("BMultiple Choice");
        typeSelect.getItems().add("Multiple Choice");
        typeSelect.getItems().add("Short Response");
        typeSelect.setValue("BMultiple Choice");

        Label type = new Label("Type:");
        type.setAlignment(Pos.CENTER);
        typeSelect.setTranslateY(-2);
        typeSelect.setTranslateX(10);
        javafx.scene.control.CheckBox imageCheckBox = new javafx.scene.control.CheckBox("Embed Image?");
        imageCheckBox.setSelected(true);
        imageCheckBox.setTranslateX(38);
        imageCheckBox.setTranslateY(2);
        ChoiceBox<String> correct = new ChoiceBox<>();
        Label correctLabel = new Label("Correct:");
        correct.getItems().add("A");
        correct.getItems().add("B");
        correct.getItems().add("C");
        correct.getItems().add("D");
        correct.setValue("A");
        correctLabel.setTranslateX(20);
        correct.setTranslateX(25);
        correct.setTranslateY(-2);
        correctLabel.setTranslateY(2);
        HBox typeBox = new HBox(type, typeSelect, correctLabel,correct, imageCheckBox);

        TextField ansA = new TextField("Response A");
        TextField ansB = new TextField("Response B");
        TextField ansC = new TextField("Response C");
        TextField ansD = new TextField("Response D");
        VBox answers = new VBox(ansA,ansB,ansC,ansD);
        answers.setSpacing(8);

        TextField questionID = new TextField("Question X");
        TextField paper = new TextField("{Paper Y}");
        TextField outcomes = new TextField("CH");
        TextField band =  new TextField("{Band}");
        TextField dirID = new TextField(String.valueOf(dirNum));
        TextField marks = new TextField("Marks");
        HBox dataBox = new HBox(questionID,paper,outcomes,band,dirID,marks);
        dataBox.setSpacing(15);

        TextArea sampleResponse = new TextArea("Sample Response");
        sampleResponse.setMinHeight(30);
        sampleResponse.setMaxHeight(96);
        TextField keywords = new TextField("Keywords/Phrases {word1,phrase here,word2,word3}");
        VBox responseFields = new VBox(sampleResponse,keywords);
        responseFields.setVisible(false);
        responseFields.setManaged(false);
        responseFields.setSpacing(21);

        javafx.scene.control.Button makeButton = new javafx.scene.control.Button("Create Question");
        javafx.scene.control.Button defsButton = new javafx.scene.control.Button("Create Outcome Defs");

        defsButton.setOnAction(event -> {categoryManager.checkDefs();});
        makeButton.setOnAction(event -> {
            JSONObject obj = new JSONObject();
            obj.put("type",typeSelect.getValue());
            obj.put("question",questionField.getText());
            if(imageCheckBox.isSelected()) {
                obj.put("image", "Y");
            }else{
                obj.put("image","N");
            }
            if(typeSelect.getValue()!="Short Response") {
                JSONArray arr = new JSONArray();
                JSONObject inArr = new JSONObject();
                inArr.put("A", ansA.getText());
                inArr.put("B", ansB.getText());
                inArr.put("C", ansC.getText());
                inArr.put("D", ansD.getText());
                arr.put(inArr);
                obj.put("answers", arr);
                obj.put("correct", correct.getValue());
            }else{
                obj.put("sample",sampleResponse.getText());
                obj.put("keywords",keywords.getText());
            }
            JSONObject data = new JSONObject();
            data.put("questionID", questionID.getText());
            data.put("paper",paper.getText());
            if(outcomes.getText().equalsIgnoreCase("CH")){
                data.put("outcomes","unk");
            }else {
                data.put("outcomes", outcomes.getText());
            }
            if(band.getText().equalsIgnoreCase("band")) {
                data.put("band","unk");
            }else {
                data.put("band", band.getText());
            }
            obj.put("data",data);
            obj.put("marks",Integer.parseInt(marks.getText()));
            dirNum=Integer.parseInt(dirID.getText());
            File theDir = new File(String.valueOf("data/questions/"+dirNum));
            if (!theDir.exists()){
                theDir.mkdirs();
            }
            try {
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("data/questions/"+(dirNum)+"/questionData.json")));
                bw.write(obj.toString());
                bw.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(imageCheckBox.isSelected()&&getImageFromClipboard()!=null) {
                imageIoWrite((BufferedImage) getImageFromClipboard());
            }

            categoryManager.checkDefs();

            dirNum++;
            dirID.setText(String.valueOf(dirNum));
            questionField.setText("");
            typeSelect.setValue("BMultiple Choice");
            imageCheckBox.setSelected(true);
            ansA.setText("Response A");
            ansB.setText("Response B");
            ansC.setText("Response C");
            ansD.setText("Response D");
            correct.setValue("A");
//            questionID.setText("Question X");
//            paper.setText("Paper Y");
            outcomes.setText("CH");
//            band.setText("{Band} X");
//            marks.setText("Marks");
        });



        typeSelect.setOnAction(event -> {
            if(typeSelect.getValue()=="Short Response"){
                answers.setVisible(false);
                answers.setManaged(false);
                responseFields.setVisible(true);
                responseFields.setManaged(true);
                correct.setVisible(false);
                correct.setManaged(false);
                correctLabel.setManaged(false);
                correctLabel.setVisible(false);
            }
            if(typeSelect.getValue()=="Multiple Choice"||typeSelect.getValue()=="BMultiple Choice"){
                answers.setVisible(true);
                answers.setManaged(true);
                responseFields.setVisible(false);
                responseFields.setManaged(false);
                correct.setVisible(true);
                correct.setManaged(true);
                correctLabel.setVisible(true);
                correctLabel.setManaged(true);
            }
        });
        HBox buttons = new HBox(makeButton,defsButton);
        buttons.setSpacing(10);
        VBox mainPanels = new VBox(questionBox,typeBox,dataBox,answers,responseFields,buttons);
        mainPanels.setSpacing(22);
        mainPanels.setPadding(new Insets(10,10,10,10));
        Scene scene = new Scene(mainPanels, 800, 450);
        primaryStage.setScene(scene);
        primaryStage.show();

    }

    public static void main(String[] args) {
        Application.launch(args);
    }


    public static void imageIoWrite(BufferedImage i) {
        BufferedImage bImage = i;
        try {
            ImageIO.write(bImage, "png", new File("data/questions/"+dirNum+"/image.png"));

        } catch (IOException e) {
            System.out.println("Exception occured :" + e.getMessage());
        }
        System.out.println("Images were written succesfully.");
    }

    public Image getImageFromClipboard()
    {
        Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor))
        {
            try
            {
                return (Image) transferable.getTransferData(DataFlavor.imageFlavor);
            }
            catch (UnsupportedFlavorException e)
            {
                // handle this as desired
                e.printStackTrace();
            }
            catch (IOException e)
            {
                // handle this as desired
                e.printStackTrace();
            }
        }
        else
        {
            System.err.println("getImageFromClipboard: That wasn't an image!");
        }
        return null;
    }
}
