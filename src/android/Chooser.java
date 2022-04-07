package in.foobars.cordova;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;

import com.itextpdf.text.pdf.PdfReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Chooser extends CordovaPlugin {
    private static final String ACTION_OPEN = "getFiles";
    private static final int PICK_FILE_REQUEST = 1;
    private static final String TAG = "Chooser";
    public static File rootDirectory = null;

    public static String getDisplayName(ContentResolver contentResolver, Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        Cursor metaCursor = contentResolver.query(uri, projection, null, null, null);

        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    return metaCursor.getString(0);
                }
            } finally {
                metaCursor.close();
            }
        }

        return null;
    }

    private CallbackContext callback;

    public void chooseFile(CallbackContext callbackContext, String accept) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          if(accept.equals("image/*")) {
            intent.setType("image/*");
        }else if(accept.equals("application/pdf")){
            intent.setType("application/pdf");
        }else{
            intent.setType("image/*|application/pdf");
        }
        intent.putExtra(Intent.EXTRA_MIME_TYPES, accept.split(","));
        intent.addCategory(Intent.CATEGORY_OPENABLE);
      //  intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, false);

        Intent chooser = Intent.createChooser(intent, "Select File");
        cordova.startActivityForResult(this, chooser, Chooser.PICK_FILE_REQUEST);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.callback = callbackContext;
        callbackContext.sendPluginResult(pluginResult);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        rootDirectory = new File(cordova.getActivity().getExternalFilesDir(""), "");
        try {
            if (action.equals(Chooser.ACTION_OPEN)) {
                this.chooseFile(callbackContext, args.getString(0));
                return true;
            }
        } catch (JSONException err) {
            this.callback.error("Execute failed: " + err.toString());
        }

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (requestCode == Chooser.PICK_FILE_REQUEST && this.callback != null) {
                if (resultCode == Activity.RESULT_OK) {
                    JSONArray files = new JSONArray();
                    if (data.getClipData() != null) {
                        for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                            files.put(processFileUri(data.getClipData().getItemAt(i).getUri()));
                        }
                        this.callback.success(files.toString());
                    } else if (data.getData() != null) {
                        files.put(processFileUri(data.getData()));
                        this.callback.success(files.toString());
                    } else {
                        this.callback.error("File URI was null.");
                    }
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    this.callback.error("RESULT_CANCELED");
                } else {
                    this.callback.error(resultCode);
                }
            }
        } catch (Exception err) {
            this.callback.error("Failed to read file: " + err.toString());
        }
    }

    public JSONObject processFileUri(Uri uri) {
        String fileName = null, extension = null, filePath = null;
        ContentResolver contentResolver = this.cordova.getActivity().getContentResolver();
        String name = Chooser.getDisplayName(contentResolver, uri);
        String mediaType = contentResolver.getType(uri);
        Boolean lockStatus = false;
        Cursor cursor = null;
        if (uri.getScheme().equals("file")) {
            fileName = uri.getLastPathSegment();
        } else {
            try {
                cursor = cordova.getActivity().getApplicationContext().getContentResolver().query(uri, new String[]{ MediaStore.Images.ImageColumns.DISPLAY_NAME }, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        filePath = copyFileFromUri(cordova.getActivity().getApplicationContext(), uri, fileName, callback);
        File file1 = new File(filePath);
        if (mediaType == null || mediaType.isEmpty()) {
            mediaType = "application/octet-stream";
        }
        JSONObject file = new JSONObject();
        try {
            file.put("mediaType", mediaType);
            file.put("name", name);
            file.put("filePath", "file://"+file1.getPath());
            file.put("uri", uri.toString());
            file.put("lockStatus", IsPasswordProtected(file1));
        } catch (JSONException err) {
            this.callback.error("Processing failed: " + err.toString());
        }
        return file;
    }
    public String copyFileFromUri(Context context, Uri fileUri, String fileName, CallbackContext callback) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        String outPutFilePath = null;
        try {
            ContentResolver content = context.getContentResolver();
            inputStream = content.openInputStream(fileUri);
            File saveDirectory = new File(rootDirectory + "/" + "images");
            saveDirectory.mkdirs();
            outPutFilePath = saveDirectory + "/" + fileName;
            outputStream = new FileOutputStream(outPutFilePath);
            if(outputStream != null) {
                byte[] buffer = new byte[1000];
                int bytesRead = 0;
                while ((bytesRead = inputStream.read( buffer, 0, buffer.length )) >= 0) {
                    outputStream.write( buffer, 0, buffer.length );
                }
            } else {
                callback.error("Error occurred in opening outputStream");
            }
        } catch (Exception e) {
            callback.error("Exception occurred " + e.getMessage());
        }
        return outPutFilePath;
    }
     public boolean IsPasswordProtected(File pdfFullname) {
        boolean isValidPdf = false;
        try {
            InputStream tempStream = new FileInputStream(pdfFullname);
            PdfReader reader = new PdfReader(tempStream);
            isValidPdf = reader.isOpenedWithFullPermissions();
            System.out.println(isValidPdf);
        } catch (Exception e) {
            System.out.println(e);
            isValidPdf = false;
        }
        return isValidPdf;
    }
}
