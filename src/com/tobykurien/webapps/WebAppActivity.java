package com.tobykurien.webapps;

import java.util.HashMap;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;

import com.tobykurien.webapps.db.DbService;
import com.tobykurien.webapps.utils.Dependencies;
import com.tobykurien.webapps.utils.Settings;

/**
 * Extensions to the main activity for Android 3.0+, or at least it used to be. Now the core
 * functionality is in the base class and the UI-related stuff is here.
 * @author toby
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class WebAppActivity extends BaseWebAppActivity {
   // variables to track dragging for actionbar auto-hide
   protected float startX;
   protected float startY;
   Settings settings;
   
   private void addShortcut(String shortcutName) {
	    //Adding shortcut for MainActivity 
	    //on Home screen
	    Intent shortcutIntent = new Intent(getApplicationContext(),
	    		WebAppActivity.class);

	    shortcutIntent.setAction(Intent.ACTION_MAIN);

	    Intent addIntent = new Intent();
	    addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
	    addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
	    addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
	            Intent.ShortcutIconResource.fromContext(getApplicationContext(),
	                    R.drawable.ic_launcher));

	    addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
	    getApplicationContext().sendBroadcast(addIntent);
	}

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      settings = Settings.getSettings(this);
      if (settings.isFullscreen()) {
         getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                                 WindowManager.LayoutParams.FLAG_FULLSCREEN);
      }
      
      // setup actionbar
      ActionBar ab = getActionBar();
      ab.setDisplayShowTitleEnabled(false);
      ab.setDisplayHomeAsUpEnabled(true);
      
      autohideActionbar();
   }

   @Override
   protected void onResume() {
      super.onResume();
      
      // may not be neccessary, but reload the settings
      settings = Settings.getSettings(this);
   }
   
   private void block3rdPartyDomains() {
       new AlertDialog.Builder(this)
       .setTitle(R.string.blocked_root_domains)
       .setMultiChoiceItems(
    		   webClient.getBlockedHosts(), 
    		   null, 
    		   new OnMultiChoiceClickListener() {
          @Override
          public void onClick(DialogInterface d, int pos, boolean checked) {
             if (checked) {
                unblock.add(webClient.getBlockedHosts()[pos].intern());
             } else {
                unblock.remove(webClient.getBlockedHosts()[pos].intern());
             }
          }
       })
       .setPositiveButton(R.string.unblock, new OnClickListener() {
          @Override
          public void onClick(DialogInterface d, int pos) {
             webClient.unblockDomains(unblock); 
             webView.reload();
             d.dismiss();
          }
       })
       .create()
       .show();
   }
   
   private void saveAsDesktopShortcut() {
       final View dlgView = LayoutInflater.from(this).inflate(
    		   R.layout.dlg_save_desktop_shortcut, 
    		   null);
       final TextView shortcutName = 
    		   (TextView) dlgView.findViewById(R.id.txtShortcutName);
       shortcutName.setText(webView.getTitle());

	   new AlertDialog.Builder(this)
	   .setTitle(R.string.dialog_save_desktop_shortcut)
	   .setView(dlgView)
	   .setPositiveButton(R.string.btn_save, new OnClickListener() {

			@Override
			public void onClick(DialogInterface d, int pos) {
			    Intent shortcutIntent = new Intent(getApplicationContext(),
			    		WebAppActivity.class);

			    shortcutIntent.setAction(Intent.ACTION_MAIN);
			    Intent addIntent = new Intent();
			    addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, 
			    		shortcutIntent);
			    addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, 
			    		shortcutName.getText().toString());
			    addIntent.putExtra(
			    		Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
			            Intent.ShortcutIconResource.fromContext(
			            		getApplicationContext(),
			                    R.drawable.ic_launcher));

			    addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
			    getApplicationContext().sendBroadcast(addIntent);
	            d.dismiss();
			}

	   })          
	   .create()
       .show();;
   }
   
   private void saveWebSite() {
       final View dlgView = LayoutInflater.from(this).inflate(
    		   R.layout.dlg_save, 
    		   null);
       final TextView name = (TextView) dlgView.findViewById(R.id.txtName);
       name.setText(webView.getTitle());
       
       new AlertDialog.Builder(this)
          .setTitle(R.string.title_save_webapp)
          .setView(dlgView)
          .setPositiveButton(R.string.btn_save, new OnClickListener() {
             @Override
             public void onClick(DialogInterface d, int pos) {
                DbService db = Dependencies.getDb(WebAppActivity.this);
                HashMap<String, Object> values = new HashMap<String, Object>();
                values.put("name", name.getText());
                values.put("url", webView.getUrl());
                values.put("iconUrl", "");
                
                if (webappId > 0) {
                   db.update(DbService.TABLE_WEBAPPS, values, String.valueOf(webappId));
                } else {
                   db.insert(DbService.TABLE_WEBAPPS, values);
                }
                
                // save the unblock list
                if (unblock.size() > 0) {
                   // clear current list
                   HashMap<String,Object> params = new HashMap<String,Object>();
                   params.put("webappId", webappId);
                   db.execute(R.string.dbDeleteDomains, params);
                   
                   // add new items
                   for (String domain : unblock) {
                      params.put("domain", domain);
                      db.insert(DbService.TABLE_DOMAINS, params);
                   }
                }
                
                d.dismiss();
             }
          })
          .create()
          .show();
   }
   
   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      if (item.getItemId() == android.R.id.home) {
         finish();
         return true;
      }
      
      if (item.getItemId() == R.id.menu_3rd_party) {
         // show blocked 3rd party domains and allow user to allow them
    	  block3rdPartyDomains();
      }
      
      if (item.getItemId() == R.id.menu_save_desktop_shortcut) {
          // save the current site as desktop shortcut
    	  saveAsDesktopShortcut();
       }
      
      if (item.getItemId() == R.id.menu_save) {
    	  saveWebSite();
      }

      return super.onOptionsItemSelected(item);
   }
   
   /**
    * Attempt to make the actionBar auto-hide and auto-reveal based on drag,
    * but unfortunately makes the bit under the actionbar mostly inaccessible,
    * so leaving this out for now.
    * @param activity
    * @param webView
    */
   public void autohideActionbar() {
      webView.setOnTouchListener(new OnTouchListener() {
         @Override
         public boolean onTouch(View arg0, MotionEvent event) {
            if (settings.isHideActionbar()) {
               if (event.getAction() == MotionEvent.ACTION_DOWN) {
                  startY = event.getY();
               }

               if (event.getAction() == MotionEvent.ACTION_MOVE) {
                  // avoid juddering by waiting for large-ish drag
                  if (Math.abs(startY - event.getY()) > 
                     new ViewConfiguration().getScaledTouchSlop() * 5) {
                     if (startY < event.getY()) 
                        getActionBar().show();
                     else
                        getActionBar().hide();
                  }
               }
            }

            return false;
         }
      });
   }
}
