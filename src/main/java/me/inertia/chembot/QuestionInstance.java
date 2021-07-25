package me.inertia.chembot;

import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.CertainMessageEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static me.inertia.chembot.Main.getDiffColor;

class QuestionInstance {

    //region Variables
    String user, question, answer, footerMessage, questionInstructions, sampleResponse;
    String QID;
    boolean showImage, isMultipleChoice;
    boolean isAnswered = false;
    String questionID = "Question N/A";
    String paper = "Paper N/A";
    String outcomes = "Outcomes N/A";
    String band = "Band N/A";
    String difficulty = "easy";
    QuestionType type;
    CertainMessageEvent event;
    ArrayList<String> keyPhrases = new ArrayList<>();
    int marks;
    QuestionsUserInstance parent;
    QuestionInstance instance = this;
    //endregion

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
        if(obj.getString("type").equalsIgnoreCase("short response")) type = QuestionType.SHORTANS;
        if(obj.getString("type").equalsIgnoreCase("extresponse")) type = QuestionType.LONGANS;
        if(type == QuestionType.SHORTANS){
            sampleResponse = obj.getString("sample");
            String[] temp =  (obj.getString("keywords").split(","));
            Collections.addAll(keyPhrases, temp);
        }
        question = obj.getString("question");
        isMultipleChoice = false;
        showImage = false;
        if(obj.getString("image").equalsIgnoreCase("y")) showImage = true;
        ArrayList<String> answers = new ArrayList<>();
        if(type == QuestionType.MULTICHOICE) {
            answer = obj.getString("correct");
            JSONArray arr = obj.getJSONArray("answers");
            answers.add(arr.getJSONObject(0).getString("A"));
            answers.add(arr.getJSONObject(0).getString("B"));
            answers.add(arr.getJSONObject(0).getString("C"));
            answers.add(arr.getJSONObject(0).getString("D"));
        }
        if (type==QuestionType.NO_LIST_ANS_CHOICE) {
            answer = obj.getString("correct");
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
        questionInstructions = "";
        if(type==QuestionType.MULTICHOICE) questionInstructions = "A: "+answers.get(0)+"\nB: "+answers.get(1)+"\nC: "+answers.get(2)+"\nD: "+answers.get(3)+"\n\n";
        if(type==QuestionType.LONGANS||type==QuestionType.SHORTANS) questionInstructions = "\n**Start your message with a '!' to submit the message as your response**";
        footerMessage = "You have " + Math.round(18f * marks)/10f + " minutes to answer!\nSource: " + paper + " | " + questionID + " | Outcomes " + outcomes + " | Band " + band + "\nID#" + (Integer.parseInt(ID) + 1);
        if(showImage)sendImageMessage();
        if(!showImage)sendMessage();
        createAnswerListener();
    }

    private void sendMessage(){
        new MessageBuilder()
                .setEmbed(new EmbedBuilder()
                        .setTitle(paper +" "+ questionID + " - " + marks + " mark(s)")
                        .setDescription(question+"\n\n"+ questionInstructions)
                        .setFooter(footerMessage)
                        .setColor(getDiffColor(difficulty)))
                .send(event.getChannel());
    }

    private void sendImageMessage(){
        new MessageBuilder()
                .setEmbed(new EmbedBuilder()
                        .setTitle(paper +" "+ questionID + " - " + marks + " mark(s)")
                        .setDescription(question+"\n"+ questionInstructions)
                        .setImage(new File("data/questions/"+QID+"/image.png"))
                        .setFooter(footerMessage)
                        .setColor(getDiffColor(difficulty)))
                .send(event.getChannel());
    }

    private void sendSampleResponse(){
        new MessageBuilder()
                .setEmbed(new EmbedBuilder()
                        .setTitle(paper +" "+ questionID + " Sample Response")
                        .setDescription(sampleResponse)
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
                    if(type.equals(QuestionType.SHORTANS)){
                        if(!event2.getMessageContent().startsWith("!")) return;
                        isAnswered = true;
                        float total = keyPhrases.size();
                        float match = 0;
                        //ArrayList<String> messageWords = (ArrayList<String>) Arrays.asList(event2.getMessageContent().toLowerCase().replaceAll("\\.|,","").split(" "));
                        String userResponse = event2.getMessageContent().toLowerCase().replaceAll("\\.|,","");
                        for (String s:keyPhrases) {
                            if(userResponse.contains(s.toLowerCase())) match++;
                        }
                        String response = "Oops! Something has gone wrong on my end and I'm not quite sure what to say here, sorry!";
                        String estimatedMark = "Your estimated mark based on your use of keywords/phrases is: "+Math.round((match/total*100))+"%. ("+Math.round((match/total)*(float)marks)+"/"+marks+" marks)";
                        if(match/total<0.4) response = "You appear to be missing a fair amount of keywords, try to review the sample responses to get a better idea of what the examiners are looking for!";
                        if(match/total>=0.4) response = "You're hitting some of the keywords, but you are forgetting to mention some critical information to really push yourself further, keep developing your knowledge!";
                        if(match/total>=0.7) response = "You appear to be comfortable with the topic at hand, you just need to get a better understanding of what 'meta-language' the examiner is looking for!";
                        if(match/total>=0.9) response = "You've basically got it down pat, so long as you maintain and reinforce your current level of knowledge, you'll be golden!";
                        new MessageBuilder()
                                .setEmbed(new EmbedBuilder()
                                        .setTitle(paper +" "+ questionID + " - " + marks + " mark(s)")
                                        .setDescription("Your response matched with roughly "+(int)match+" out of the "+(int)total+" key words/phrases.\n"+estimatedMark+"\n\n"+response)
                                        .setFooter("Please remember that feedback is only based off of keywords and does not take into account most synonyms, spelling errors, and typos. The bot cannot fact-check, only tell you if you're using the right terminology.")
                                        .setColor(getDiffColor(difficulty)))
                                .send(event2.getChannel());
                        sendSampleResponse();
                        return;
                    }
                    if(type.equals(QuestionType.LONGANS)){

                    }
                }
            }
        }).removeAfter((long) (marks*60f*1.8f), TimeUnit.SECONDS)
                .addRemoveHandler(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Question timed out!");
                        if(!isAnswered) {
                            try {
                                if(type!=QuestionType.SHORTANS) {
                                    event.getChannel().sendMessage("Time's up, " + Main.api.getUserById(user).get().getMentionTag() + "! The answer was: " + answer);
                                }else{
                                    event.getChannel().sendMessage("Time's up! Here's a sample response of what your answer should look like:");
                                    sendSampleResponse();
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                e.printStackTrace();
                            }
                        }
                        parent.finaliseQuestion(instance);
                    }
                });
        System.out.println("Listener Created!");
    }
}
