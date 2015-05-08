/* ***************************************************************** */
/*                                                                   */
/* IBM Confidential                                                  */
/*                                                                   */
/* OCO Source Materials                                              */
/*                                                                   */
/* Copyright IBM Corp. 2013                                          */
/*                                                                   */
/* The source code for this program is not published or otherwise    */
/* divested of its trade secrets, irrespective of what has been      */
/* deposited with the U.S. Copyright Office.                         */
/*                                                                   */
/* ***************************************************************** */
package com.ibm.cio.audio;

import java.io.IOException;
import java.io.OutputStream;

import com.ibm.cio.watsonsdk.SpeechRecorderDelegate;

// TODO: Auto-generated Javadoc
/**
 * Non-encode.
 */
public class VaniRawEnc implements VaniEncoder{
    // Use PROPRIETARY notice if class contains a main() method, otherwise use
    // COPYRIGHT notice.
    public static final String COPYRIGHT_NOTICE = "(c) Copyright IBM Corp. 2013";
    /** Output stream */
    private OutputStream out;
    private SpeechRecorderDelegate delegate = null;
    /**
     * Constructor.
     */
    public VaniRawEnc() {
    }

    /* (non-Javadoc)
     * @see com.ibm.cio.audio.VaniEncoder#initEncodeAndWriteHeader(java.io.OutputStream)
     */
    @Override
    public void initEncodeAndWriteHeader(OutputStream out) throws IOException {
        this.out = out;
    }

    /* (non-Javadoc)
     * @see com.ibm.cio.audio.VaniEncoder#encodeAndWrite(byte[])
     */
    @Override
    public int encodeAndWrite(byte[] b) throws IOException {
        out.write(b);
        if(this.delegate != null)
            this.delegate.onRecordingCompleted(b);
        return b.length;
    }
    /* (non-Javadoc)
     * @see com.ibm.cio.audio.VaniEncoder#close()
     */
    @Override
    public void close() {
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public long getCompressionTime() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public byte[] encode(byte[] b) {
        // TODO Auto-generated method stub
        return b;
    }

    @Override
    public void writeChunk(byte[] b) throws IOException {
        // TODO Auto-generated method stub
        out.write(b);
    }

    @Override
    public void setDelegate(SpeechRecorderDelegate obj) {
        // TODO Auto-generated method stub
        this.delegate = obj;
    }

    @Override
    public void initEncoderWithWebSocketClient(ChuckWebSocketUploader client)
            throws IOException {
        // TODO Auto-generated method stub

    }
}
