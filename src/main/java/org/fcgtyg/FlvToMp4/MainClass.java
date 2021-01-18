package org.fcgtyg.FlvToMp4;

public class MainClass {
    public static void main(String[] args){
        String inputPath;
        boolean debug = false;
        if (args.length < 1) {
            //System.out.println("Missing input video file");
            //System.exit(-1);
            inputPath = "C:/Users/gulme/IdeaProjects/FlvToMp4/src/main/java/org/fcgtyg/FlvToMp4/sample-1.flv";
            debug = false;
        }else {
            inputPath = args[0];
            if(args.length > 1)
                debug = args[1].equals("debug");
        }
        Converter converter = new Converter(inputPath, debug);
        converter.startConvertingPipeline();
    }
}
