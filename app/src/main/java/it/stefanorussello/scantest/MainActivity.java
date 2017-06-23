package it.stefanorussello.scantest;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Stefano Russello on 19/06/17.
 *
 * The application starts with this class. It works in portrait and landscape mode.
 */

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_BARCODE = 1;
    private static final String webService = "https://api.upcitemdb.com/prod/trial/lookup?upc=";
    private static final String TAG = "MainActivity";
    private TextView txtBarcode;
    private ProgressDialog pDialog;
    private OkHttpClient httpClient = new OkHttpClient();
    private ListView listItems;
    private ArrayList<ItemData> items;
    private ItemsAdapter itemsAdapter;
    private DisplayMetrics screenScale;
    private boolean panelOpened = false;
    private boolean firstAnimation = true;
    private RelativeLayout layoutList;
    private RelativeLayout screenLayout;
    private RelativeLayout layoutButtons;
    private Button btnPanel;
    private ImageView imgLogo;
    private TextView txtNoItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        screenScale = getResources().getDisplayMetrics();

        // Initializing visual components
        txtBarcode = (TextView)findViewById(R.id.txtBarcode);
        listItems = (ListView)findViewById(R.id.listItems);
        layoutList = (RelativeLayout)findViewById(R.id.layoutList);
        layoutButtons = (RelativeLayout)findViewById(R.id.layoutButtons);
        screenLayout = (RelativeLayout)findViewById(R.id.screenLayout);
        btnPanel = (Button)findViewById(R.id.btnPanel);
        imgLogo = (ImageView)findViewById(R.id.imgLogo);
        txtNoItems = (TextView)findViewById(R.id.txtNoItems);

        items = new ArrayList<ItemData>();

        itemsAdapter = new ItemsAdapter();
        listItems.setAdapter(itemsAdapter);

        layoutList.setVisibility(View.INVISIBLE);
        layoutButtons.setVisibility(View.INVISIBLE);

        // Listener to confirm barcode from virtual keyboard
        txtBarcode.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == event.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(txtBarcode.getWindowToken(), 0);
                    getInfoButton(null);
                }
                return false;
            }
        });

        // Insert icon on Action Bar
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.mipmap.icon);
        getSupportActionBar().setTitle("\t" + getResources().getString(R.string.title_app));
    }


    /**
     * Called at the beginnig only for logo animation
     */
    @Override
    protected void onStart() {

        if (firstAnimation) {
            firstAnimation = false;
            // Resize logo to fit full screen
            imgLogo.getLayoutParams().height = screenLayout.getLayoutParams().height;
            imgLogo.getLayoutParams().width = screenLayout.getLayoutParams().width;

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Reduce height animation
                    ValueAnimator slideUp = ValueAnimator.ofInt(imgLogo.getMeasuredHeight(), (int) (100 * screenScale.density));
                    slideUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            int val = (Integer) animation.getAnimatedValue();
                            ViewGroup.LayoutParams layoutParams = imgLogo.getLayoutParams();
                            layoutParams.height = val;
                            imgLogo.setLayoutParams(layoutParams);
                        }
                    });
                    slideUp.setDuration(2000);
                    slideUp.start();

                    Animation alpha = new AlphaAnimation(0f, 1f);
                    alpha.setDuration(1000);
                    alpha.setStartOffset(2000);
                    layoutButtons.setVisibility(View.VISIBLE);
                    layoutList.setVisibility(View.VISIBLE);
                    layoutButtons.startAnimation(alpha);
                    layoutList.startAnimation(alpha);
                }
            }, 2000);
        }
        super.onStart();
    }

    /**
     * Called when the camera button is pressed.
     * In this way the user can scan the barcode instead of manually inserting it.
     * When the ScannerActivity will be closed, the function onActivityResult will be called
     * @param view Button view
     */
    public void startScanner(View view) {

        // Calling ScannerActivity that return the barcode string onActivityResult function
        Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
        startActivityForResult(intent, REQUEST_BARCODE);
    }

    /**
     * Called when the "Get Information" button is pressed
     * @param view Button view
     */
    public void getInfoButton(View view) {

        // Barcode validation
        if (txtBarcode.getText().length() > 0) {

            getInfo(txtBarcode.getText().toString());
        
        } else {

            Toast.makeText(this, getResources().getString(R.string.barcode_should_filled), Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Get the bar code information
     * @param barcode String of the barcode to check on internet
     */
    private void getInfo(final String barcode) {

        // Showing loading dialog
        pDialog = new ProgressDialog(this);
        pDialog.setMessage(getResources().getString(R.string.wait));
        pDialog.setCancelable(false);
        pDialog.show();

        // Composing web-service url
        String url = webService + barcode;

        // Getting barcode information from web-service
        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Request request, final IOException e) {
                Log.d(TAG, "onFailure: " + e.getLocalizedMessage());
                pDialog.dismiss();

                // In case of failure, showing error information
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, getResources().getString(R.string.error_get_info) + " " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onResponse(Response response) throws IOException {

                pDialog.dismiss();

                // Getting json string from http response
                final String jsonStr = response.body().string();

                if (jsonStr != null) {

                    try {

                        // Variable for validated barcode
                        boolean codeOk = false;
                        String codeMsg = getResources().getString(R.string.error_no_barcode);

                        // Convert json string to JSONObject
                        JSONObject jsonObj = new JSONObject(jsonStr);

                        // If barcode is OK
                        if (jsonObj.getString("code").equals("OK")) {

                            codeOk = true;
                            // Getting items node (array)
                            JSONArray arrItems = jsonObj.getJSONArray("items");

                            Log.d(TAG, "onResponse: " + jsonStr);

                            // Check if item node has items inside
                            if (arrItems.length() > 0) {

                                // Getting information from json
                                JSONObject objItem = arrItems.getJSONObject(0);
                                String title = objItem.optString("title", null);
                                String brand = objItem.optString("brand", null);
                                String sn = objItem.optString("asin", null); // If ASIN does not exist, I put barcode as Serial Number
                                String image = null;
                                String link = null;
                                if (objItem.getJSONArray("images").length() > 0) {
                                    image = objItem.getJSONArray("images").optString(0, null);
                                }
                                if (objItem.getJSONArray("offers").length() > 0) {
                                    link = objItem.getJSONArray("offers").getJSONObject(0).optString("link", null);
                                }

                                // Check if the barcode already exists
                                boolean exists = false;
                                for (ItemData item: items) {
                                    if (item.barcode.equals(barcode)) {
                                        exists = true;
                                        break;
                                    }
                                }

                                // Insert new barcode in the list
                                if (exists == false) {
                                    ItemData newItem = new ItemData(title, brand, sn, barcode, image, link);
                                    items.add(newItem);
                                }

                                final boolean finalExists = exists;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {

                                        // Refreshing barcode list
                                        itemsAdapter.refresh();
                                        if (!panelOpened) {
                                            // Open panel automatically after a scanned barcode
                                            togglePanel(null);
                                        }

                                        txtBarcode.setText("");

                                        if (finalExists) {
                                            Toast.makeText(MainActivity.this, getResources().getString(R.string.itemexists), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                            } else {
                                codeOk = false;
                            }
                        } else {

                            // Composing message if server response code is not ok
                            codeMsg = jsonObj.getString("code") + ": " + jsonObj.getString("message");
                        }

                        if (codeOk == false) {

                            // Showing message if barcode is wrong or details not available
                            final String finalCodeMsg = codeMsg;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, finalCodeMsg, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                    } catch (final JSONException e) {
                        e.printStackTrace();

                        // Showing message if json does not follow the standard
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, getResources().getString(R.string.error_json) + ": " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }

        });
    }

    /**
     * Called when an Activity called with startActivityForResult will be closed
     * @param requestCode a code passed on startActivityForResult
     * @param resultCode a code passed from closed Activity
     * @param data the data passed from closed Activity
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Getting barcode string from ScannerActivity
        if (requestCode == REQUEST_BARCODE) {
            if (resultCode == RESULT_OK) {
                //Use Data to get string
                String strBarcode = data.getStringExtra("RESULT_STRING");

                txtBarcode.setText(strBarcode);

                // Get information automatically after scanning a barcode
                getInfo(strBarcode);
            }
        }
    }

    /**
     * Function to open and close the bottom panel for showing the list of items
     * @param view Button vire
     */
    public void togglePanel(View view) {

        ValueAnimator slideUp = null;

        if (panelOpened) {
            // Close animation
            slideUp = ValueAnimator.ofInt(layoutList.getMeasuredHeight(), (int)(40 * screenScale.density));
        } else {
            // Open animation
            slideUp = ValueAnimator.ofInt(layoutList.getMeasuredHeight(), screenLayout.getMeasuredHeight());
        }

        slideUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int val = (Integer) animation.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = layoutList.getLayoutParams();
                layoutParams.height = val;
                layoutList.setLayoutParams(layoutParams);
            }
        });
        slideUp.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationStart(Animator animation) {}
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationRepeat(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {
                // Rotate arrow animation
                Animation rotate = new RotateAnimation(panelOpened ? 0f : 180f, panelOpened ? 180f : 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                rotate.setFillAfter(true);
                rotate.setDuration(500);
                btnPanel.startAnimation(rotate);
            }
        });

        slideUp.setDuration(1000);
        slideUp.start();

        panelOpened = !panelOpened;

        if (itemsAdapter.getCount() > 0 && panelOpened) {
            // Scroll to the last item inserted
            listItems.smoothScrollToPosition(itemsAdapter.getCount()-1);
        }
    }

    /**
     * Function called when the clean button (recycle bin) is pressed
     * @param view Button view
     */
    public void clearList(View view) {

        // Check if there is unless an item
        if (items.size() > 0) {

            // Showing dialog to choose if deleting item/s
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setTitle(getResources().getString(R.string.delete));
            builder.setMessage(getResources().getString(R.string.deleteitems));

            builder.setPositiveButton(getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {

                    // Delete all items in the list
                    items.clear();
                    itemsAdapter.refresh();

                    if (panelOpened) {
                        // Close panel if opened after deleting all items
                        togglePanel(null);
                    }

                    dialog.dismiss();
                }
            });

            builder.setNegativeButton(getResources().getString(R.string.no), new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {

                    // Do nothing
                    dialog.dismiss();
                }
            });

            AlertDialog alert = builder.create();
            alert.show();

        } else {

            // No items in the list
            Toast.makeText(this, getResources().getString(R.string.nodelete), Toast.LENGTH_LONG).show();
        }
    }

    // Adapter to manage all items in the list
    private class ItemsAdapter extends BaseAdapter {

        public void refresh()
        {
            notifyDataSetChanged();
            txtNoItems.setVisibility(items.size() > 0 ? View.INVISIBLE : View.VISIBLE);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ItemData item = items.get(position);

            // Composing item card
            View itemView = getLayoutInflater().inflate(R.layout.item, null);
            ImageView imgItem = (ImageView) itemView.findViewById(R.id.imgItem);
            TextView lblTitle = (TextView) itemView.findViewById(R.id.lblTitle);
            TextView lblBrand = (TextView) itemView.findViewById(R.id.lblBrand);
            TextView lblSN = (TextView) itemView.findViewById(R.id.lblSN);
            Button btnMoreInfo = (Button) itemView.findViewById(R.id.btnMoreInfo);

            // Loading item image
            if (item.image != null && item.image.length() > 0) {
                Picasso.with(MainActivity.this).load(item.image).into(imgItem);
            }

            if (item.title != null) {
                lblTitle.setText(item.title);
            } else {
                lblTitle.setVisibility(View.GONE);
            }

            if (item.brand != null) {
                lblBrand.setText(item.brand);
            } else {
                lblBrand.setVisibility(View.GONE);
            }

            if (item.serialnumber != null) {
                lblSN.setText(item.serialnumber);
            } else {
                if (item.barcode != null) {
                    lblSN.setText(item.barcode);
                } else {
                    lblSN.setVisibility(View.GONE);
                }
            }

            // Generating link when the item button is pressed
            btnMoreInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intentLink = new Intent(Intent.ACTION_VIEW);
                    intentLink.setData(Uri.parse(item.link));
                    startActivity(intentLink);
                }
            });

            return itemView;
        }
    }
}
