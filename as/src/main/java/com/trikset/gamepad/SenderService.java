package com.trikset.gamepad;

import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

final class SenderService {
    private OnEventListener<String> getShowTextCallback() {
        return mShowTextCallback;
    }

    void setShowTextCallback(OnEventListener<String> mShowTextCallback) {
        this.mShowTextCallback = mShowTextCallback;
    }

    private OnEventListener<String> getOnDisconnectedListener() {
        return mOnDisconnectedListener;
    }

    void setOnDisconnectedListener(OnEventListener<String> mOnDisconnectedListener) {
        this.mOnDisconnectedListener = mOnDisconnectedListener;
    }

    interface IShowTextCallback {
        void show(String text);
    }

    private static final int TIMEOUT = 5000;
    private final Object mSyncFlag = new Object();
    private OnEventListener<String> mShowTextCallback;
    @Nullable
    private PrintWriter mOut;

    private OnEventListener<String> mOnDisconnectedListener;

    private String mHostAddr;

    private int mHostPort;
    @Nullable
    private AsyncTask<Void, Void, PrintWriter> mConnectTask;

    SenderService() {
    }

    private void connectAsync() {
        synchronized (this) {
            if (mConnectTask != null)
                return;
            mConnectTask = new PrintWriterAsyncTask();
            mConnectTask.execute();
        }
    }


    // socket is closed from PrintWriter.close()
    @SuppressWarnings("resource")
    synchronized private PrintWriter connectToTRIK() {

        try {
            Log.e("TCP Client", "C: Connecting...");
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(mHostAddr, mHostPort), TIMEOUT);

            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoLinger(true, 0);
            socket.setTrafficClass(0x0F); // high priority, no-delay
            socket.setOOBInline(true);
            socket.shutdownInput();
            OutputStreamWriter osw =
                    //Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                    //new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8):
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8");


            // currently does nothing
            // socket.setPerformancePreferences(connectionTime, latency,
            // bandwidth);
            try {
                return new PrintWriter(osw, true);
            } catch (@NonNull final Exception e) {
                Log.e("TCP", "GetStream: Error", e);
                socket.close();
                osw.close();
            }
        } catch (@NonNull final IOException e) {
            Log.e("TCP", "Connect: Error", e);
        }
        // mLastConnectionAttemptTimestamp = currentTime;
        return null;
    }

    void disconnect(final String reason) {
        if (mOut != null) {
            mOut.close();
            mOut = null;
            Log.d("TCP", "Disconnected.");
            OnEventListener<String> l = getOnDisconnectedListener();
            if (l != null)
                l.onEvent(reason);
        }
    }

    String getHostAddr() {
        return mHostAddr;
    }

    void send(final String command) {

        if (mOut == null) {
            connectAsync();
            // Data loss here! Nevermind ...
            return;
        }

        Log.d("TCP", "Sending '" + command + '\'');

        new SendCommandAsyncTask(command).execute();
    }

    void setTarget(@NonNull final String hostAddr, final int hostPort) {
        if (!hostAddr.equalsIgnoreCase(mHostAddr) || mHostPort != hostPort) {
            disconnect("Target changed.");
        }

        mHostAddr = hostAddr;
        mHostPort = hostPort;
    }

    interface OnEventListener<ArgType> {
        void onEvent(ArgType arg);
    }

    private class PrintWriterAsyncTask extends AsyncTask<Void, Void, PrintWriter> {
        @Nullable
        @Override
        protected PrintWriter doInBackground(final Void... params) {
            return connectToTRIK();
        }

        @Override
        protected void onPostExecute(PrintWriter result) {
            mOut = result;
            OnEventListener<String> cb = getShowTextCallback();
            if (cb != null) {
                cb.onEvent("Connection to " + mHostAddr + ':' + mHostPort
                        + (mOut != null ? " established." : " error."));
            }
            (new ResetToNullAsyncTask()).execute();
        }

        private class ResetToNullAsyncTask extends AsyncTask<Void, Void, Void> {
            @Nullable
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    Thread.sleep(TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                synchronized (SenderService.this) {
                    mConnectTask = null;
                }
                return null;
            }
        }
    }

    private class SendCommandAsyncTask extends AsyncTask<Void, Void, Void> {
        private final String command;

        SendCommandAsyncTask(String command) {
            this.command = command;
        }

        @Nullable
        @Override
        protected Void doInBackground(final Void... params) {
            synchronized (mSyncFlag) {
                // TODO: reimplement with Handle instead of multiple chaotic
                // AsyncTasks
                if (mOut != null)
                    mOut.println(command);
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void result) {
            if (mOut == null || mOut.checkError()) {
                Log.e("TCP", "NotSent: " + command);
                disconnect("Send failed.");
            }
        }

    }
}
