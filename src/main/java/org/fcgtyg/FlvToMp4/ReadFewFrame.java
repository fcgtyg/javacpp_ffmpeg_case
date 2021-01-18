package org.fcgtyg.FlvToMp4;
import java.io.*;
import org.bytedeco.javacpp.*;
import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.swscale.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avfilter.av_buffersink_get_frame;
import static org.bytedeco.ffmpeg.global.avfilter.av_buffersrc_add_frame_flags;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

public class ReadFewFrame {
//    if (videoFilterGraph != null){
//        AVFrame frame = av_frame_alloc();
//        try{
//            av_packet_rescale_ts(packet, inputVideoStream.time_base(), videoDecoderCodecContext.time_base());
//            log(avcodec_send_packet(videoDecoderCodecContext, packet));
//            log(avcodec_receive_frame(videoDecoderCodecContext, frame));
//            frame.pts(frame.best_effort_timestamp());
//
//            av_buffersrc_add_frame_flags(videoBufferSrcContext, frame, 0);
//
//            while (true) {
//                AVFrame filterFrame = av_frame_alloc();
//
//                int ret = av_buffersink_get_frame(videoBufferSinkContext, filterFrame);
//
//                /* if no more frames for output - returns AVERROR(EAGAIN)
//                 * if flushed and no more frames for output - returns AVERROR_EOF
//                 * rewrite retcode to 0 to show it as normal procedure completion
//                 */
//                if (ret == AVERROR_EOF() || ret == AVERROR_EAGAIN()) {
//                    av_frame_free(filterFrame);
//                    break;
//                }
//
//                //check(ret);
//
//                filterFrame.pict_type(AV_PICTURE_TYPE_NONE);
//
//                AVPacket encodedPacket = new AVPacket();
//                /* encode filtered frame */
//                encodedPacket.data(null);
//                encodedPacket.size(0);
//
//                av_init_packet(encodedPacket);
//
//                int[] gotFrameLocal = new int[1];
//
//                //log(avcodec_encode_video2(videoEncoderCodecContext, encodedPacket, filterFrame, gotFrameLocal));
//                log(avcodec_send_frame(videoEncoderCodecContext, filterFrame));
//                log(avcodec_receive_packet(videoEncoderCodecContext, encodedPacket));
//
//                av_frame_free(filterFrame);
//
//                if (gotFrameLocal[0] == 0) {
//                    //break;
//                }
//
//                /* prepare packet for muxing */
//                encodedPacket.stream_index(inputVideoStreamIndex);
//
//                av_packet_rescale_ts(encodedPacket, videoEncoderCodecContext.time_base(),
//                        outputFormatContext.streams(inputVideoStreamIndex).time_base());
//
//                /* mux encoded frame */
//                av_interleaved_write_frame(outputFormatContext, encodedPacket);
//            }
//
//        }finally {
//            av_frame_free(frame);
//        }
//
//    }else{
//        av_packet_rescale_ts(packet, inputVideoStream.time_base(),
//                outputVideoStream.time_base());
//        av_interleaved_write_frame(outputFormatContext, packet);
//    }
}