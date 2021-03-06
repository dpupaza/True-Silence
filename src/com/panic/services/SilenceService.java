/*
 * Copyright (c) 2007-2011 Scott Rahner, Panic Productions, a division N1 Concepts LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 * http://www.opensource.org/licenses/mit-license.php
 */

package com.panic.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.panic.R;
import com.panic.widgets.SilenceWidget;

public class SilenceService extends Service {
	
	@Override
	public void onStart(Intent intent, int startId) {
		Log.i("tsilence", "Staring Silence Service");
		
		boolean swap=false;
		boolean isSilent = getSilenceState(this);
		
		Bundle extras = intent.getExtras();
		if(extras != null && extras.containsKey("swap")){
			swap=extras.getBoolean("swap");
		}
		
		int[] streamIds = {//AudioManager.STREAM_VOICE_CALL, 
							AudioManager.STREAM_SYSTEM, 
							AudioManager.STREAM_RING, 
							AudioManager.STREAM_MUSIC,
							AudioManager.STREAM_ALARM,
							AudioManager.STREAM_NOTIFICATION
							};
		
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
		int[] widgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, SilenceWidget.class));
		
		Intent reIntent = new Intent(this, SilenceService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, reIntent, 0);
		
		RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.silence_widget_layout);
		if(swap){
			Log.i("tsilence","Swapping Silent State");
			isSilent = !isSilent;
		}
		
		setSilenceState(this, views, isSilent);
		views.setOnClickPendingIntent(R.id.silence_layout, pendingIntent);
		appWidgetManager.updateAppWidget(widgetIds, views);
		
		AudioManager am = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
		
		if(!isSilent && swap){
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			if(prefs.getBoolean("silenceRefresh", false)){
				startService(new Intent(this, EnforceSilenceService.class));
			}
		}
		
		for(int id : streamIds){
			if(!isSilent){
				if(swap){
					setStreamPref(this, id, am.getStreamVolume(id));
					//need to start service only when true silence is turning on
				}
			 	am.setStreamVolume(id,0,AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
			 	//Notification On
			 	NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				Notification notif = new Notification(R.drawable.sound_muted, getString(R.string.app_name), System.currentTimeMillis());
				notif.flags |= Notification.FLAG_ONGOING_EVENT;
				Intent notifIntent = new Intent(this,SilenceService.class);
				notifIntent.putExtra("swap", true);
				PendingIntent pi = PendingIntent.getService(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
				notif.setLatestEventInfo(this, getString(R.string.app_name), getString(R.string.notification_text), pi);
				nm.notify(1, notif);
			}else{
				if(getStreamPref(this, id) != 0){ //Fixes bug w/ linked streams
					am.setStreamVolume(id,getStreamPref(this, id),AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
				}
				//Service off
				stopService(new Intent(this, EnforceSilenceService.class));
				//Notification Off
				NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				nm.cancel(1);
			}
		}
		
		stopSelf();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private void setStreamPref(Context context, int streamId, int streamVol){
		SharedPreferences.Editor prefEdit = context.getSharedPreferences(getString(R.string.prefName), Context.MODE_PRIVATE).edit();
		prefEdit.putInt(new Integer(streamId).toString(), streamVol);
		prefEdit.commit();
	}
	
	private int getStreamPref(Context context, int streamId) {
        SharedPreferences prefs = context.getSharedPreferences(getString(R.string.prefName), Context.MODE_PRIVATE);
        return prefs.getInt(new Integer(streamId).toString(),7);
    }
	
	private void setSilenceState(Context context, RemoteViews views, boolean isSilent) {
        SharedPreferences.Editor prefEdit = context.getSharedPreferences(getString(R.string.prefName), Context.MODE_PRIVATE).edit();
        prefEdit.putBoolean("silent", isSilent);
        prefEdit.commit();
        setSilenceIcon(context, views);
    }
	
	private static boolean getSilenceState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(context.getString(R.string.prefName), Context.MODE_PRIVATE);
        return prefs.getBoolean("silent",false);
    }
    
	public static void setSilenceIcon(Context context, RemoteViews views){
		if(getSilenceState(context)){
			views.setImageViewResource(R.id.ImageView01, R.drawable.sound_icon);
	    }else{
	    	views.setImageViewResource(R.id.ImageView01, R.drawable.sound_off_icon);
	    }
	}
	
}