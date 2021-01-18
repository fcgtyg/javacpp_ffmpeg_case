package org.fcgtyg.FlvToMp4;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avfilter.AVFilter;
import org.bytedeco.ffmpeg.avfilter.AVFilterContext;
import org.bytedeco.ffmpeg.avfilter.AVFilterGraph;
import org.bytedeco.ffmpeg.avfilter.AVFilterInOut;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avfilter;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avfilter.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avformat.av_dump_format;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class Converter {
    private final boolean debug;

    public Converter(String filePath, boolean debug){
        inputPath = filePath;
        outputPath = inputPath.replace(".flv", ".mp4");
        this.debug = debug;
    }

    public void startConvertingPipeline(){
        print("Extracting audio and video streams...");
        extractStreams();
        print("Generating encoders for input streams...");
        generateTranscoders();
        print("Generating output streams...");
        generateOutputStreams();
        print("Generating filters...");
        generateFilter();
        print("Converting...");
        convert();
    }

    private void extractStreams(){
        inputFormatContext = new AVFormatContext(null);
        if( avformat_open_input(inputFormatContext, inputPath, null, null) <0){
            System.out.println("Cannot Open");
            System.exit(-1);
        }

        if (avformat_find_stream_info(inputFormatContext, (PointerPointer)null) < 0) {
            System.exit(-1);
        }

        for(int streamIndex = 0; streamIndex< inputFormatContext.nb_streams(); streamIndex++) {
            AVStream stream = inputFormatContext.streams(streamIndex);
            if(stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO){
                inputVideoStream = stream;
                inputVideoStreamIndex = streamIndex;
            }
            else if(stream.codecpar().codec_type() == AVMEDIA_TYPE_AUDIO){
                inputAudioStream = stream;
                inputAudioStreamIndex = streamIndex;
            }
        }

        checkForStreams();
    }

    private void checkForStreams(){
        hasAudio = inputAudioStream != null;
        hasVideo = inputVideoStream != null;
        if(!hasAudio)
            print("No audio stream found.");
        if(!hasVideo)
            print("No video stream found");
        if(!hasAudio & !hasVideo){
            print("Nothing to convert. Exiting");
            System.exit(0);
        }
    }

    private void generateTranscoders(){
        generateDecoder(inputVideoStream, true);
        generateDecoder(inputAudioStream, false);
        av_dump_format(inputFormatContext, 0, inputPath, 0);

        generateEncoder();

    }

    private void generateDecoder(AVStream stream, boolean isVideo){
        if(stream == null)
            return;
        AVCodecParameters streamCodecParameters = stream.codecpar();

        AVCodec decoderCodec = avcodec_find_decoder(streamCodecParameters.codec_id());
        AVCodecContext decoderContext = avcodec_alloc_context3(decoderCodec);
        avcodec_parameters_to_context(decoderContext, streamCodecParameters);
        if(isVideo)
            decoderContext.framerate(av_guess_frame_rate(inputFormatContext, inputVideoStream, null));
        avcodec_open2(decoderContext, decoderCodec, (PointerPointer)null);
        if(isVideo){
            videoDecoderCodecContext = decoderContext;
        }else{
            audioDecoderCodecContext = decoderContext;
        }
    }

    private void generateEncoder(){
        outputFormatContext = new AVFormatContext(null);
        avformat_alloc_output_context2(outputFormatContext, null, null, outputPath);

        generateVideoEncoder();
        generateAudioEncoder();

    }

    private void generateVideoEncoder(){
        if(!hasVideo)
            return;
        videoEncoderCodec = avcodec_find_encoder(AV_CODEC_ID_H264);
        videoEncoderCodecContext = avcodec_alloc_context3(videoEncoderCodec);
        videoEncoderCodecContext.height(videoDecoderCodecContext.height());
        videoEncoderCodecContext.width(videoDecoderCodecContext.width());
        videoEncoderCodecContext.sample_aspect_ratio(videoDecoderCodecContext.sample_aspect_ratio());
        if (videoEncoderCodec.pix_fmts() != null && videoEncoderCodec.pix_fmts().asBuffer() != null)
            videoEncoderCodecContext.pix_fmt(videoEncoderCodec.pix_fmts().get(0));
        else
            videoEncoderCodecContext.pix_fmt(videoDecoderCodecContext.pix_fmt());
        videoEncoderCodecContext.time_base(av_inv_q(videoDecoderCodecContext.framerate()));

        avcodec_open2(videoEncoderCodecContext, videoEncoderCodec, (AVDictionary) null);
    }

    private void generateAudioEncoder(){
        if(!hasAudio)
            return;
        audioEncoderCodec =  avcodec_find_encoder(AV_CODEC_ID_AAC);
        audioEncoderCodecContext = avcodec_alloc_context3(audioEncoderCodec);
        audioEncoderCodecContext.sample_rate(audioDecoderCodecContext.sample_rate());
        audioEncoderCodecContext.channel_layout(audioDecoderCodecContext.channel_layout());
        audioEncoderCodecContext.channels(av_get_channel_layout_nb_channels(audioDecoderCodecContext.channel_layout()));
        audioEncoderCodecContext.sample_fmt(audioEncoderCodec.sample_fmts().get(0));
        audioEncoderCodecContext.time_base(av_make_q(1, audioEncoderCodecContext.sample_rate()));
        int i = audioDecoderCodecContext.frame_size();
        audioEncoderCodecContext.frame_size(i);

        avcodec_open2(audioEncoderCodecContext, audioEncoderCodec, (AVDictionary) null);
    }

    private void generateOutputStreams(){
        if(hasAudio){
            AVStream outputAudioStream = avformat_new_stream(outputFormatContext, audioEncoderCodec);
            avcodec_parameters_from_context(outputAudioStream.codecpar(), audioEncoderCodecContext);
            if ((outputFormatContext.oformat().flags() & AVFMT_GLOBALHEADER) == AVFMT_GLOBALHEADER) {

                audioEncoderCodecContext.flags(audioEncoderCodecContext.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
            }

            outputAudioStream.time_base(audioEncoderCodecContext.time_base());
        }
        if(hasVideo){
            AVStream outputVideoStream = avformat_new_stream(outputFormatContext, videoEncoderCodec);
            avcodec_parameters_from_context(outputVideoStream.codecpar(), videoEncoderCodecContext);
            if ((outputFormatContext.oformat().flags() & AVFMT_GLOBALHEADER) == AVFMT_GLOBALHEADER) {
                videoEncoderCodecContext.flags(videoEncoderCodecContext.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
            }
            outputVideoStream.time_base(videoEncoderCodecContext.time_base());
        }

        av_dump_format(outputFormatContext, 0, outputPath, 1);

        if ((outputFormatContext.flags() & AVFMT_NOFILE) != AVFMT_NOFILE) {
            AVIOContext c = new AVIOContext();
            avio_open(c, outputPath, AVIO_FLAG_WRITE);
            outputFormatContext.pb(c);
        }

        avformat_write_header(outputFormatContext, (AVDictionary) null);
    }

    private void generateFilter(){
        generateVideoFilter();
        generateAudioFilter();
    }

    private void generateVideoFilter(){
        if(!hasVideo)
            return;

        AVFilter videoBufferSink = avfilter.avfilter_get_by_name("buffersink");
        AVFilter videoBufferSrc = avfilter.avfilter_get_by_name("buffer");

        videoFilterGraph = avfilter_graph_alloc();
        videoBufferSrcContext = new AVFilterContext();
        videoBufferSinkContext = new AVFilterContext();

        String args = String.format("video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
                videoDecoderCodecContext.width(),
                videoDecoderCodecContext.height(),
                videoDecoderCodecContext.pix_fmt(),
                videoDecoderCodecContext.time_base().num(),
                videoDecoderCodecContext.time_base().den(),
                videoDecoderCodecContext.sample_aspect_ratio().num(),
                videoDecoderCodecContext.sample_aspect_ratio().den());

        log(avfilter_graph_create_filter(videoBufferSrcContext, videoBufferSrc, "in", args, null, videoFilterGraph));
        log(avfilter_graph_create_filter(videoBufferSinkContext, videoBufferSink, "out", null, null, videoFilterGraph));

        BytePointer pixFmt = new BytePointer(4).putInt(videoEncoderCodecContext.pix_fmt());
        log(av_opt_set_bin(videoBufferSinkContext, "pix_fmts", pixFmt, 4, AV_OPT_SEARCH_CHILDREN));

        processFilterIO(videoBufferSrcContext, videoBufferSinkContext, videoFilterGraph, "null");
    }


    private void generateAudioFilter(){
        if(!hasAudio)
            return;

        AVFilter audioBufferSink = avfilter.avfilter_get_by_name("abuffersink");
        AVFilter audioBufferSrc = avfilter.avfilter_get_by_name("abuffer");

        audioFilterGraph = avfilter_graph_alloc();
        audioBufferSrcContext = new AVFilterContext();
        audioBufferSinkContext = new AVFilterContext();

        if (audioDecoderCodecContext.channel_layout() == 0) {
            audioDecoderCodecContext.channel_layout(av_get_default_channel_layout(audioDecoderCodecContext.channels()));
        }

        BytePointer name = new BytePointer(100);

        av_get_channel_layout_string(name, 100, audioDecoderCodecContext.channels(), audioDecoderCodecContext.channel_layout());
        String chLayout = name.getString().substring(0, (int) BytePointer.strlen(name));
        String args = String.format("time_base=%d/%d:sample_rate=%d:sample_fmt=%s:channel_layout=%s",
                audioDecoderCodecContext.time_base().num(),
                audioDecoderCodecContext.time_base().den(),
                audioDecoderCodecContext.sample_rate(),
                av_get_sample_fmt_name(audioDecoderCodecContext.sample_fmt()).getString(), chLayout);

        log(avfilter_graph_create_filter(audioBufferSrcContext, audioBufferSrc, "in", args, null, audioFilterGraph));
        log(avfilter_graph_create_filter(audioBufferSinkContext, audioBufferSink, "out", null, null, audioFilterGraph));

        BytePointer smplFmt = new BytePointer(4).putInt(audioEncoderCodecContext.sample_fmt());
        av_opt_set_bin(audioBufferSinkContext, "sample_fmts", smplFmt, 4, AV_OPT_SEARCH_CHILDREN);

        BytePointer chL = new BytePointer(8).putLong(audioEncoderCodecContext.channel_layout());
        av_opt_set_bin(audioBufferSinkContext, "channel_layouts", chL, 8, AV_OPT_SEARCH_CHILDREN);

        BytePointer sr = new BytePointer(8).putLong(audioEncoderCodecContext.sample_rate());
        av_opt_set_bin(audioBufferSinkContext, "sample_rates", sr, 8, AV_OPT_SEARCH_CHILDREN);

        processFilterIO(audioBufferSrcContext, audioBufferSinkContext, audioFilterGraph, "anull");
    }

    private void processFilterIO(AVFilterContext bufferSrcContext,
                                 AVFilterContext bufferSinkContext,
                                 AVFilterGraph filterGraph,
                                 String filterSpec){
        AVFilterInOut outputs = avfilter_inout_alloc();
        AVFilterInOut inputs = avfilter_inout_alloc();
        try {
            outputs.name(new BytePointer(av_strdup("in")));
            outputs.filter_ctx(bufferSrcContext);
            outputs.pad_idx(0);
            outputs.next(null);

            inputs.name(new BytePointer(av_strdup("out")));
            inputs.filter_ctx(bufferSinkContext);
            inputs.pad_idx(0);
            inputs.next(null);

            log(avfilter_graph_parse_ptr(filterGraph, filterSpec, inputs, outputs, null));
            log(avfilter_graph_config(filterGraph, null));
        }finally {
            avfilter_inout_free(outputs);
            avfilter_inout_free(inputs);
        }
    }

    private void convert(){
        try {
            AVPacket packet = new AVPacket();
            while (av_read_frame(inputFormatContext, packet)>=0){
                try {
                    if(packet.stream_index() == inputVideoStreamIndex){
                        processPacket(packet, true);
                        /**/
                    }else if(packet.stream_index() == inputAudioStreamIndex){
                        processPacket(packet, false);
                        //avcodec_encode_audio2(audioEncoderCodecContext, encodedPacket, filterFrame, gotFrameLocal);
                    }
                }finally {
                    av_packet_unref(packet);
                }
            }
            cleanUp();
            av_write_trailer(outputFormatContext);
        }finally {
            avcodec_free_context(videoDecoderCodecContext);
            avcodec_free_context(audioDecoderCodecContext);

            if (outputFormatContext != null && outputFormatContext.nb_streams() > 0 ) {
                if(videoEncoderCodecContext != null &&
                        outputFormatContext.streams(inputVideoStreamIndex) != null)
                    avcodec_free_context(videoEncoderCodecContext);
                if(audioEncoderCodecContext != null &&
                        outputFormatContext.streams(inputAudioStreamIndex) != null)
                    avcodec_free_context(audioEncoderCodecContext);
            }

            if (videoFilterGraph != null) {
                avfilter_graph_free(videoFilterGraph);
            }
            if (audioFilterGraph != null) {
                avfilter_graph_free(audioFilterGraph);
            }
            avformat_close_input(inputFormatContext);

            if (outputFormatContext != null && (outputFormatContext.oformat().flags() & AVFMT_NOFILE) != AVFMT_NOFILE) {
                avio_closep(outputFormatContext.pb());
            }

            avformat_free_context(outputFormatContext);
        }
    }

    private void cleanUp(){
        if(videoFilterGraph != null){
            filterEncodeWriteFrame(null, true);
            if((videoEncoderCodecContext.codec().capabilities() & AV_CODEC_CAP_DELAY) == AV_CODEC_CAP_DELAY)
                while(encodeWriteFrame(null, true));
        }
        if(audioFilterGraph != null){
            filterEncodeWriteFrame(null, false);
            if((audioEncoderCodecContext.codec().capabilities() & AV_CODEC_CAP_DELAY) == AV_CODEC_CAP_DELAY)
                while(encodeWriteFrame(null, false));
        }
    }

    private void processPacket(AVPacket packet, boolean isVideo){
        AVFilterGraph filterGraph;
        AVCodecContext decoderCodecContext;
        int streamIndex;
        if(isVideo){
            filterGraph = videoFilterGraph;
            streamIndex = inputVideoStreamIndex;
            decoderCodecContext = videoDecoderCodecContext;
        }else{
            filterGraph = audioFilterGraph;
            streamIndex = inputAudioStreamIndex;
            decoderCodecContext = audioDecoderCodecContext;
        }
        if (filterGraph != null) {
            AVFrame frame = av_frame_alloc();

            try {
                av_packet_rescale_ts(packet, inputFormatContext.streams(streamIndex).time_base(),
                        decoderCodecContext.time_base());

                log(avcodec_send_packet(decoderCodecContext, packet));
                log(avcodec_receive_frame(decoderCodecContext, frame));

                frame.pts(frame.best_effort_timestamp());
                filterEncodeWriteFrame(frame, isVideo);
            } finally {
                av_frame_free(frame);
            }
        } else {
            /* remux this frame without reencoding */
            av_packet_rescale_ts(packet, inputFormatContext.streams(streamIndex).time_base(),
                    outputFormatContext.streams(streamIndex).time_base());
            log(av_interleaved_write_frame(outputFormatContext, packet));
        }
    }

    private void filterEncodeWriteFrame(AVFrame frame, boolean isVideo) {
        AVFilterContext bufferSrcContext, bufferSinkContext;
        if(isVideo){
            bufferSrcContext = videoBufferSrcContext;
            bufferSinkContext = videoBufferSinkContext;
        }else{
            bufferSrcContext = audioBufferSrcContext;
            bufferSinkContext = audioBufferSinkContext;
        }
        log(av_buffersrc_add_frame_flags(bufferSrcContext, frame, 0));

        /* pull filtered frames from the filtergraph */
        while (true) {
            AVFrame filterFrame = av_frame_alloc();

            int ret = av_buffersink_get_frame(bufferSinkContext, filterFrame);

            /* if no more frames for output - returns AVERROR(EAGAIN)
             * if flushed and no more frames for output - returns AVERROR_EOF
             * rewrite retcode to 0 to show it as normal procedure completion
             */
            if (ret == AVERROR_EOF() || ret == AVERROR_EAGAIN()) {
                av_frame_free(filterFrame);
                return;
            }

            log(ret);

            filterFrame.pict_type(AV_PICTURE_TYPE_NONE);
            encodeWriteFrame(filterFrame, isVideo);
        }
    }

    private boolean encodeWriteFrame(AVFrame filterFrame, boolean isVideo) {
        int streamIndex;
        AVCodecContext encoderContext;
        if(isVideo){
            streamIndex = inputVideoStreamIndex;
            encoderContext = videoEncoderCodecContext;
        }else{
            streamIndex = inputAudioStreamIndex;
            encoderContext = audioEncoderCodecContext;
        }
        AVPacket encodedPacket = new AVPacket();
        /* encode filtered frame */
        encodedPacket.data(null);
        encodedPacket.size(0);

        av_init_packet(encodedPacket);
        int isFlushed;
        if (isVideo) {
            log(avcodec_send_frame(videoEncoderCodecContext, filterFrame));
            isFlushed =avcodec_receive_packet(videoEncoderCodecContext, encodedPacket);
        } else {
            log(avcodec_send_frame(audioEncoderCodecContext, filterFrame));
            isFlushed = avcodec_receive_packet(audioEncoderCodecContext, encodedPacket);
        }

        av_frame_free(filterFrame);

        if (isFlushed == AVERROR_EOF) {
            return false;
        }

        /* prepare packet for muxing */
        encodedPacket.stream_index(streamIndex);

        av_packet_rescale_ts(encodedPacket,encoderContext.time_base(),
                outputFormatContext.streams(streamIndex).time_base());

        /* mux encoded frame */
        log(av_interleaved_write_frame(outputFormatContext, encodedPacket));

        return true;
    }

    private int inputVideoStreamIndex, inputAudioStreamIndex;
    private boolean hasVideo, hasAudio;
    private final String outputPath;
    private final String inputPath;
    private AVFormatContext inputFormatContext, outputFormatContext;
    private AVStream inputAudioStream;
    private AVStream inputVideoStream;
    private AVCodec videoEncoderCodec;
    private AVCodec audioEncoderCodec;
    private AVCodecContext videoDecoderCodecContext, audioDecoderCodecContext,
            videoEncoderCodecContext, audioEncoderCodecContext;

    private AVFilterContext videoBufferSinkContext, videoBufferSrcContext, audioBufferSinkContext, audioBufferSrcContext;
    private AVFilterGraph videoFilterGraph, audioFilterGraph;


    private static void print(Object o){
        System.out.println(o);
    }

    private void log(int code){
        if(code >= 0 | !debug)
            return;
        byte[] errPointer = new byte[1024];
        av_strerror(code, errPointer, errPointer.length);
        String message = new String(errPointer, 0, errPointer.length);

        print(message);
    }
}
