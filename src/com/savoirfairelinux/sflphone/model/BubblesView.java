/*
 *  Copyright (C) 2004-2013 Savoir-Faire Linux Inc.
 *
 *  Author: Alexandre Lision <alexandre.lision@savoirfairelinux.com>
 *  Adrien Béraud <adrien.beraud@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  If you modify this program, or any covered work, by linking or
 *  combining it with the OpenSSL project's OpenSSL library (or a
 *  modified version of that library), containing parts covered by the
 *  terms of the OpenSSL or SSLeay licenses, Savoir-Faire Linux Inc.
 *  grants you additional permission to convey the resulting work.
 *  Corresponding Source for a non-source form of such a combination
 *  shall include the source code for the parts of OpenSSL used as well
 *  as that of the covered work.
 */

package com.savoirfairelinux.sflphone.model;

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;

import com.savoirfairelinux.sflphone.client.CallActivity;
import com.savoirfairelinux.sflphone.fragments.CallFragment;

public class BubblesView extends SurfaceView implements SurfaceHolder.Callback, OnTouchListener {
    private static final String TAG = BubblesView.class.getSimpleName();

    private BubblesThread thread = null;
    private BubbleModel model;

    private Paint attractor_paint = new Paint();
    private Paint black_name_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint white_name_paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private GestureDetector gDetector;

    private float density;
    private float textDensity;

    private boolean dragging_bubble = false;

    private CallFragment callback;

    public BubblesView(Context context, AttributeSet attrs) {
        super(context, attrs);

        density = getResources().getDisplayMetrics().density;
        textDensity = getResources().getDisplayMetrics().scaledDensity;

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create thread only; it's started in surfaceCreated()
        createThread();

        setOnTouchListener(this);
        setFocusable(true);

        attractor_paint.setColor(Color.RED);
        // attractor_paint.set
        black_name_paint.setTextSize(18 * textDensity);
        black_name_paint.setColor(0xFF303030);
        black_name_paint.setTextAlign(Align.CENTER);

        white_name_paint.setTextSize(18 * textDensity);
        white_name_paint.setColor(0xFFEEEEEE);
        white_name_paint.setTextAlign(Align.CENTER);

        gDetector = new GestureDetector(getContext(), new MyOnGestureListener());
    }

    private void createThread() {
        if (thread != null)
            return;
        thread = new BubblesThread(getHolder(), getContext(), new Handler() {
            @Override
            public void handleMessage(Message m) {
                /*
                 * mStatusText.setVisibility(m.getData().getInt("viz")); mStatusText.setText(m.getData().getString("text"));
                 */
            }
        });
        if (model != null)
            thread.setModel(model);
    }

    public void setModel(BubbleModel model) {
        this.model = model;
        thread.setModel(model);
    }

    /*
     * @Override public void onWindowFocusChanged(boolean hasWindowFocus) { if (!hasWindowFocus) { thread.pause(); } }
     */

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.w(TAG, "surfaceChanged " + width + "-" + height);
        thread.setSurfaceSize(width, height);
    }

    /*
     * Callback invoked when the Surface has been created and is ready to be used.
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // start the thread here so that we don't busy-wait in run()
        // waiting for the surface to be created
        createThread();

        Log.w(TAG, "surfaceCreated");
        thread.setRunning(true);
        thread.start();
    }

    /*
     * Callback invoked when the Surface has been destroyed and must no longer be touched. WARNING: after this method returns, the Surface/Canvas must
     * never be touched again!
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // we have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        Log.w(TAG, "surfaceDestroyed");
        boolean retry = true;
        thread.setRunning(false);
        thread.setPaused(false);
        while (retry) {
            try {
                Log.w(TAG, "joining...");
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
        Log.w(TAG, "done");
        thread = null;
    }

    public boolean isDraggingBubble() {
        return dragging_bubble;
    }

    class BubblesThread extends Thread {
        private boolean running = false;
        private SurfaceHolder surfaceHolder;
        public Boolean suspendFlag = false;

        BubbleModel model = null;

        public BubblesThread(SurfaceHolder holder, Context context, Handler handler) {
            surfaceHolder = holder;
        }

        public void setModel(BubbleModel model) {
            this.model = model;
        }

        @Override
        public void run() {
            while (running) {
                Canvas c = null;
                try {

                    if (suspendFlag) {
                        synchronized (this) {
                            while (suspendFlag) {
                                try {
                                    wait();
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                        }
                    } else {
                        c = surfaceHolder.lockCanvas(null);

                        // for the case the surface is destroyed while already in the loop
                        if (c == null || model == null)
                            continue;

                        synchronized (surfaceHolder) {
                            // Log.w(TAG, "Thread doDraw");
                            model.update();
                            doDraw(c);
                        }
                    }

                } finally {
                    if (c != null)
                        surfaceHolder.unlockCanvasAndPost(c);
                }
            }
        }

        public void setPaused(boolean wantToPause) {
            synchronized (this) {
                suspendFlag = wantToPause;
                notify();
            }
        }

        public void setRunning(boolean b) {
            running = b;
        }

        public void setSurfaceSize(int width, int height) {
            synchronized (surfaceHolder) {
                if (model != null) {
                    model.width = width;
                    model.height = height;
                }
            }
        }

        /**
         * I got multiple IndexOutOfBoundsException, when switching calls. //FIXME
         * 
         * @param canvas
         */
        private void doDraw(Canvas canvas) {
            canvas.drawColor(Color.WHITE);

            synchronized (model) {
                List<Bubble> bubbles = model.getBubbles();
                List<Attractor> attractors = model.getAttractors();
                try {

                    for (int i = 0, n = attractors.size(); i < n; i++) {
                        Attractor a = attractors.get(i);
                        canvas.drawBitmap(a.getBitmap(), null, a.getBounds(), null);
                    }

                    for (int i = 0, n = bubbles.size(); i < n; i++) {
                        Bubble b = bubbles.get(i);
                        canvas.drawBitmap(b.getBitmap(), null, b.getBounds(), null);
                        canvas.drawText(b.associated_call.getContact().getmDisplayName(), b.getPosX(), b.getPosY() - 40 * density, getNamePaint(b));
                    }
                    Bubble first_plan = getExpandedBubble();
                    if (first_plan != null) {
                        canvas.drawBitmap(first_plan.getBitmap(), null, first_plan.getBounds(), null);
                        canvas.drawText(first_plan.associated_call.getContact().getmDisplayName(), first_plan.getPosX(), first_plan.getPosY() - 50
                                * density, getNamePaint(first_plan));
                        canvas.drawText("Transfer", first_plan.getPosX(), first_plan.getPosY() + 70 * density, getNamePaint(first_plan));
                        if (first_plan.associated_call.isOnHold()) {
                            canvas.drawText("Unhold", first_plan.getPosX() - 70 * density, first_plan.getPosY(), getNamePaint(first_plan));
                        } else {
                            canvas.drawText("Hold", first_plan.getPosX() - 70 * density, first_plan.getPosY(), getNamePaint(first_plan));
                        }
                        if (first_plan.associated_call.isRecording()) {
                            canvas.drawText("Stop\nRecording", first_plan.getPosX() + 70 * density, first_plan.getPosY(), getNamePaint(first_plan));
                        } else {
                            canvas.drawText("Record", first_plan.getPosX() + 70 * density, first_plan.getPosY(), getNamePaint(first_plan));

                        }

                    }

                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, e.toString());
                }
            }
        }

    }

    private Paint getNamePaint(Bubble b) {
        if (b.expanded) {
            white_name_paint.setTextSize(15 * b.target_scale * textDensity);
            return white_name_paint;
        }
        black_name_paint.setTextSize(18 * b.target_scale * textDensity);
        return black_name_paint;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.w(TAG, "onTouch " + event.getAction());

        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_UP) {
            if (thread.suspendFlag) {
                Log.i(TAG, "Relaunch drawing thread");
                thread.setPaused(false);
            }

            List<Bubble> bubbles = model.getBubbles();
            final int n_bubbles = bubbles.size();
            for (int i = 0; i < n_bubbles; i++) {
                Bubble b = bubbles.get(i);
                if (b.dragged) {
                    b.dragged = false;
                    b.target_scale = 1.f;
                }
            }
            dragging_bubble = false;
        } else if (action != MotionEvent.ACTION_DOWN && !isDraggingBubble() && !thread.suspendFlag) {

            Log.i(TAG, "Not dragging thread should be stopped");
            thread.setPaused(true);
            // thread.holdDrawing();
        }

        return gDetector.onTouchEvent(event);
    }

    private Bubble getExpandedBubble() {
        List<Bubble> bubbles = model.getBubbles();
        final int n_bubbles = bubbles.size();
        for (int i = 0; i < n_bubbles; i++) {
            Bubble b = bubbles.get(i);
            if (b.expanded) {
                return b;
            }
        }
        return null;
    }

    public void restartDrawing() {
        if (thread != null && thread.suspendFlag) {
            Log.i(TAG, "Relaunch drawing thread");
            thread.setPaused(false);
        }
    }

    public void setFragment(CallFragment callFragment) {
        callback = callFragment;

    }

    public void stopThread() {
        if (thread != null && thread.suspendFlag) {
            Log.i(TAG, "Stop drawing thread");
            thread.setRunning(false);
            thread.setPaused(false);
        }

    }

    class MyOnGestureListener implements OnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            List<Bubble> bubbles = model.getBubbles();
            final int n_bubbles = bubbles.size();
            Bubble expand = getExpandedBubble();
            if (expand != null) {
                if (!expand.intersects(event.getX(), event.getY())) {
                    expand.retract();
                } else {
                    Log.d("Main", "getAction");
                    switch (expand.getAction(event.getX(), event.getY())) {
                    case 0:
                        expand.retract();
                        break;
                    case 1:
                        if (expand.associated_call.isOnHold()) {
                            ((CallActivity) callback.getActivity()).onCallResumed(expand.associated_call);
                        } else {
                            ((CallActivity) callback.getActivity()).onCallSuspended(expand.associated_call);
                        }

                        break;
                    case 2:
                        Log.d("Main", "onRecordCall");
                        ((CallActivity) callback.getActivity()).onRecordCall(expand.associated_call);
                        break;
                    case 3:
                        Toast.makeText(getContext(), "Not implemented here", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
                return true;
            }
            Log.d("Main", "onDown");
            for (int i = 0; i < n_bubbles; i++) {
                Bubble b = bubbles.get(i);
                if (b.intersects(event.getX(), event.getY()) && !b.expanded) {
                    b.dragged = true;
                    b.last_drag = System.nanoTime();
                    b.setPos(event.getX(), event.getY());
                    b.target_scale = .8f;
                    dragging_bubble = true;
                }
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            Log.d("Main", "onFling");
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            Log.d("Main", "onLongPress");
            if (isDraggingBubble()) {
                Bubble b = getDraggedBubble(e);
                b.expand((int) (100 * getResources().getDisplayMetrics().density));
            }
        }

        private Bubble getDraggedBubble(MotionEvent e) {
            List<Bubble> bubbles = model.getBubbles();
            final int n_bubbles = bubbles.size();
            for (int i = 0; i < n_bubbles; i++) {
                Bubble b = bubbles.get(i);
                if (b.intersects(e.getX(), e.getY())) {
                    return b;
                }
            }
            return null;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent event, float distanceX, float distanceY) {
            Log.d("Main", "onScroll");
            List<Bubble> bubbles = model.getBubbles();
            final int n_bubbles = bubbles.size();
            long now = System.nanoTime();
            for (int i = 0; i < n_bubbles; i++) {
                Bubble b = bubbles.get(i);
                if (b.dragged) {
                    float x = event.getX(), y = event.getY();
                    float dt = (float) ((now - b.last_drag) / 1000000000.);
                    float dx = x - b.getPosX(), dy = y - b.getPosY();
                    b.last_drag = now;
                    b.setPos(event.getX(), event.getY());
                    b.speed.x = dx / dt;
                    b.speed.y = dy / dt;
                    // }
                    return true;
                }
            }
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            Log.d("Main", "onShowPress");

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            Log.d("Main", "onSingleTapUp");
            return true;
        }
    }
}
