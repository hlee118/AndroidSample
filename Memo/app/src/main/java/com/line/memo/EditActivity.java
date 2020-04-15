package com.line.memo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.line.memo.Database.SQLite;
import com.line.memo.Database.Tables;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class EditActivity extends AppCompatActivity implements View.OnClickListener {
    private final int REQUEST_PICK_PHOTOS = 0;
    private final int REQUEST_TAKE_PHOTO = 1;
    private final int PERMISSION_CAMERA = 2;

    private int id;
    private EditText edit_title, edit_content;
    private Button btn_photo, btn_confirm, btn_back;
    private SQLiteDatabase db;
    private ContentValues values;

    private LayoutInflater inflater;
    private LinearLayout edit_scroll_view;
    private AlertDialog.Builder dialog_builder;
    private Button scrollview_item_btn_delete;
    private List<Uri> picture_uris;

    private File file;
    private Uri fileUri;

    private Context context;
    private Thread mThread = null;
    private Bitmap bitmap;
    private ConstraintLayout global_container;
    private Uri global_uri;
    private boolean global_new_flag;
    EditText dialog_edit_text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        context = this;
        db = new SQLite(this).getDatabase();

        Intent intent = getIntent();
        id = intent.getIntExtra("id", -1);

        edit_title = findViewById(R.id.edit_edit_title);
        edit_content = findViewById(R.id.edit_edit_content);
        btn_photo = findViewById(R.id.edit_btn_photo);
        btn_confirm = findViewById(R.id.edit_btn_confirm);
        btn_back = findViewById(R.id.edit_btn_back);
        edit_scroll_view = findViewById(R.id.edit_scroll_view);

        Cursor cursor = db.rawQuery("SELECT * FROM " + Tables.Memo.TABLE_NAME + " where id=" + id + ";", null);
        cursor.moveToNext();
        edit_title.setText(cursor.getString(1));
        edit_content.setText(cursor.getString(2));

        btn_back.setOnClickListener(this);
        btn_photo.setOnClickListener(this);
        btn_confirm.setOnClickListener(this);

        picture_uris = new ArrayList<>();
        inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        cursor = db.rawQuery("SELECT * FROM " + Tables.Uri.TABLE_NAME + " where memo_id=" + id + ";", null);
        while (cursor.moveToNext()) {
            String uri = cursor.getString(2);
            picture_uris.add(Uri.parse(uri));
        }

        for (int i = 0; i < picture_uris.size(); i++) {
            Uri uri = picture_uris.get(i);
            if (uri.toString().contains("http"))
                add_view_online(false, uri);
            else
                add_view(uri);
        }
    }

    public void add_view(final Uri uri) {
        final ConstraintLayout container = (ConstraintLayout) inflater.inflate(R.layout.scrollview_item, edit_scroll_view, false);
        ImageView imageView = container.findViewById(R.id.scrollview_item_image);
        imageView.setImageURI(uri);
        scrollview_item_btn_delete = container.findViewById(R.id.scrollview_item_btn_delete);
        scrollview_item_btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit_scroll_view.removeView(container);
                for (int i = 0; i < picture_uris.size(); i++) {

                    // Reference Pointer Delete
                    if (uri == picture_uris.get(i)) {
                        picture_uris.remove(i);
                        break;
                    }
                }
            }
        });
        edit_scroll_view.addView(container);
    }

    public void add_view_online(final boolean new_flag, final Uri uri) {
        final ConstraintLayout container = (ConstraintLayout) inflater.inflate(R.layout.scrollview_item, edit_scroll_view, false);
        scrollview_item_btn_delete = container.findViewById(R.id.scrollview_item_btn_delete);
        scrollview_item_btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit_scroll_view.removeView(container);
                for (int i = 0; i < picture_uris.size(); i++) {
                    // Reference Pointer Delete
                    if (uri == picture_uris.get(i)) {
                        picture_uris.remove(i);
                        return;
                    }
                }
            }
        });
        edit_scroll_view.addView(container);

        if (!uri.toString().contains("http"))
            return;

        mThread = new Thread(new Runnable() {
            ConstraintLayout temp_container = container;
            Uri temp_uri = uri;
            boolean temp_new_flag = new_flag;

            @Override
            public void run() {
                try {
                    URL url = new URL(uri.toString());
                    URLConnection conn = url.openConnection();
                    conn.connect();

                    BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());
                    bitmap = BitmapFactory.decodeStream(bis);
                    if (bitmap == null) {
                        // No Images!!!
                        throw new Exception();
                    }

                    global_container = temp_container;
                    global_uri = temp_uri;
                    global_new_flag = temp_new_flag;
                    handler.sendEmptyMessage(0);
                    bis.close();
                } catch (Exception e) {
                    global_container = temp_container;
                    handler.sendEmptyMessage(-1);
                }
            }
        });
        mThread.start();
    }

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case -1:
                    edit_scroll_view.removeView(global_container);
                    Toast.makeText(context, "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
                    break;
                case 0:
                    ImageView imageView = global_container.findViewById(R.id.scrollview_item_image);
                    imageView.setImageBitmap(bitmap);
                    if (global_new_flag)
                        picture_uris.add(global_uri);
                    global_new_flag = false;
                    break;
            }
        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.edit_btn_back:
                finish();
                break;
            case R.id.edit_btn_photo:
                CharSequence[] items = {"포토", "카메라", "외부이미지"};
                dialog_builder = new AlertDialog.Builder(this);
                dialog_builder.setTitle("사진 선택");
                dialog_builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        switch (which) {
                            case 0:
                                Intent intent = new Intent(Intent.ACTION_PICK);
                                intent.setType("image/*");
                                intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
                                intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                                startActivityForResult(Intent.createChooser(intent, "다중 선택은 해당 기능을 지원하는 애플리케이션에서만 가능합니다. (포토 등) "), REQUEST_PICK_PHOTOS);
                                break;
                            case 1:
                                int permissionCheck = ContextCompat.checkSelfPermission(EditActivity.this, Manifest.permission.CAMERA);
                                if (permissionCheck == PackageManager.PERMISSION_DENIED) {
                                    ActivityCompat.requestPermissions(EditActivity.this,
                                            new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
                                    break;
                                }
                                startCameraIntent();
                                break;
                            case 2:
                                dialog_edit_text = new EditText(context);
                                dialog_builder = new AlertDialog.Builder(context);
                                dialog_builder.setTitle("URL 입력");
                                dialog_builder.setView(dialog_edit_text);
                                dialog_builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                                dialog_builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Uri uri = Uri.parse(dialog_edit_text.getText().toString());
                                        add_view_online(true, uri);
                                        dialog.dismiss();
                                    }
                                });
                                dialog_builder.show();
                                break;
                        }
                    }
                });
                dialog_builder.show();
                break;
            case R.id.edit_btn_confirm:
                String title = edit_title.getText().toString();
                String content = edit_content.getText().toString();
                if (title.equals("") || content.equals("")) {
                    Toast.makeText(this, "Fill it all up", Toast.LENGTH_SHORT).show();
                    break;
                }

                values = new ContentValues();
                values.put("title", title);
                values.put("content", content);
                db.update(Tables.Memo.TABLE_NAME, values, "id=" + id, null);

                db.delete(Tables.Uri.TABLE_NAME, "memo_id=" + id, null);
                for (Uri uri : picture_uris) {
                    values = new ContentValues();
                    values.put("memo_id", id);
                    values.put("uri", uri.toString());
                    db.insert(Tables.Uri.TABLE_NAME, null, values);
                }

                Toast.makeText(EditActivity.this, "success", Toast.LENGTH_SHORT).show();
                finish();
                break;
        }
    }

    public void startCameraIntent() {
        Intent camera_intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            file = File.createTempFile(String.valueOf(System.currentTimeMillis()), ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
            fileUri = FileProvider.getUriForFile(this, "com.line.memo.fileprovider", file);
        } catch (IOException e) {
            return;
        }

        camera_intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(camera_intent, REQUEST_TAKE_PHOTO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    startCameraIntent();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_PICK_PHOTOS:
                if (resultCode == Activity.RESULT_OK) {

                    List<Uri> new_picture_uris = new ArrayList<>();
                    ClipData clipData = data.getClipData();
                    if (clipData == null) {
                        new_picture_uris.add(Uri.parse(String.valueOf(data.getData())));
                    } else {
                        for (int i = 0; i < clipData.getItemCount(); i++)
                            new_picture_uris.add(clipData.getItemAt(i).getUri());
                    }

                    for (int i = 0; i < new_picture_uris.size(); i++)
                        add_view(new_picture_uris.get(i));

                    picture_uris.addAll(new_picture_uris);
                }
                break;
            case REQUEST_TAKE_PHOTO:
                if (resultCode == Activity.RESULT_OK) {
                    add_view(fileUri);
                    picture_uris.add(fileUri);
                }
                break;
        }
    }
}
