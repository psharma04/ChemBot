package me.inertia.chembot;

import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.CertainMessageEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static me.inertia.chembot.Main.getDiffColor;

class QuestionInstance {
    String user;
    //String type;
    String question;
    String answer;
    boolean showImage;
    boolean isMultipleChoice;
    boolean isAnswered = false;
    String questionID = "Question N/A";
    String paper = "Paper N/A";
    String outcomes = "Outcomes N/A";
    String band = "Band N/A";
    String difficulty = "easy";
    String answersList;
    String footerMessage;
    QuestionType type;
    CertainMessageEvent event;
    int marks;
    String QID;
    QuestionsUserInstance parent;
    QuestionInstance instance = this;

    public QuestionInstance(QuestionsUserInstance parent, String ID, CertainMessageEvent event){
        this.parent = parent;
        user = event.getMessageAuthor().getIdAsString();
        this.event = event;
        QID = ID;
        generateQuestion(ID);
    }
    private void generateQuestion(String ID){
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(("data/questions/"+ ID +"/questionData.json"))));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        JSONObject obj = null;
        try {
            obj = new JSONObject(br.readLine());
        } catch (IOException e) {
            e.printStackTrace();
            jsonNotFoundException();
        }
        if(obj.getString("type").equalsIgnoreCase("multiple choice")) type = QuestionType.MULTICHOICE;
        if(obj.getString("type").equalsIgnoreCase("bmultiple choice")) type = QuestionType.NO_LIST_ANS_CHOICE;
        question = obj.getString("question");
        isMultipleChoice = false;
        answer = obj.getString("correct");
        showImage = false;
        if(obj.getString("image").equalsIgnoreCase("y")){
            //do some thing
            showImage = true;
        }
        ArrayList<String> answers = new ArrayList<>();
        if(type == QuestionType.MULTICHOICE) {
            JSONArray arr = obj.getJSONArray("answers");
            answers.add(arr.getJSONObject(0).getString("A"));
            answers.add(arr.getJSONObject(0).getString("B"));
            answers.add(arr.getJSONObject(0).getString("C"));
            answers.add(arr.getJSONObject(0).getString("D"));
        }
        if (type==QuestionType.NO_LIST_ANS_CHOICE) {
            JSONArray arr = obj.getJSONArray("answers");
            answers.add(arr.getJSONObject(0).getString("A"));
            answers.add(arr.getJSONObject(0).getString("B"));
            answers.add(arr.getJSONObject(0).getString("C"));
            answers.add(arr.getJSONObject(0).getString("D"));
        }
        marks = obj.getInt("marks");
        JSONObject extra = obj.getJSONObject("data");
        if (marks>=2) {
            difficulty = "medium";
            if(marks>=4){
                difficulty = "hard";
            }
        }
        questionID = extra.getString("questionID");
        paper = extra.getString("paper");
        band = extra.getString("band");
        outcomes = extra.getString("outcomes");
        answersList = "";
        if(type==QuestionType.MULTICHOICE) answersList = "A: "+answers.get(0)+"\nB: "+answers.get(1)+"\nC: "+answers.get(2)+"\nD: "+answers.get(3)+"\n\n";
        System.out.println(question);
        footerMessage = "You have " + 1.8f * marks + " minutes to answer!\nSource: " + paper + " | " + questionID + " | Outcomes " + outcomes + " | Band " + band + "\nID#" + (Integer.parseInt(ID) + 1);
        if(showImage)sendImageMessage();
        if(!showImage)sendMessage();
        createAnswerListener();
    }

    private void sendMessage(){
        new MessageBuilder()
                .setEmbed(new EmbedBuilder()
                        .setTitle(paper +" "+ questionID + " - " + marks + " mark(s)")
                        .setDescription(question+"\n\n"+answersList)
                        .setFooter(footerMessage)
                        .setColor(getDiffColor(difficulty)))
                .send(event.getChannel());
    }

    private void sendImageMessage(){
        new MessageBuilder()
                .setEmbed(new EmbedBuilder()
                        .setTitle(paper +" "+ questionID + " - " + marks + " mark(s)")
                        .setDescription(question+"\n"+answersList)
                        .setImage(new File("data/questions/"+QID+"/image.png"))
                        .setFooter(footerMessage)
                        .setColor(getDiffColor(difficulty)))
                .send(event.getChannel());
    }

    private void jsonNotFoundException(){
        event.getChannel().sendMessage("There was an error retrieving the file, please try again!");
        System.out.println("Error reading JSON file, maybe empty file?");
    }

    private void createAnswerListener(){
        System.out.println("Creating Listener");
        Main.api.addMessageCreateListener(event2 -> {
            if(event2.getMessageAuthor().getIdAsString().trim().equals(user.trim())&&!isAnswered){
                System.out.println("Passed user ID check");
                if(type==QuestionType.MULTICHOICE||type==QuestionType.NO_LIST_ANS_CHOICE) {
                    System.out.println("Passed multiple choice check");
                    if (event2.getMessageContent().trim().equalsIgnoreCase(answer)) {
                        event2.getChannel().sendMessage(":white_check_mark: Correct! Excellent job! (+" + marks + " marks)");
                        isAnswered = true;
                        if(event.isServerMessage()) {
                            Main.awardMarks(Main.api, event2.getServer().get().getIdAsString(), event2.getMessageAuthor().getIdAsString(), marks);
                        }
                    }else{
                        String s = "abcd";
                        if(s.contains(event2.getMessageContent().toLowerCase().trim())){
                            isAnswered = true;
                            event2.getChannel().sendMessage(":x: Incorrect, the answer was "+answer+". Better luck next time!");
                        }
                    }
                }else{
                    //do extended/short response keyword check method here
                }
            }
        }).removeAfter((long) (marks*60f*1.8f), TimeUnit.SECONDS)
                .addRemoveHandler(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Question timed out!");
                        if(!isAnswered) {
                            try {
                                event.getChannel().sendMessage("Time's up, "+Main.api.getUserById(user).get().getMentionTag()+"! The answer was: " + answer);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            }
                        }
                        parent.finaliseQuestion(instance);
                    }
                });
        System.out.println("Listener Created!");
    }
}
