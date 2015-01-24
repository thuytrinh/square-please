package com.thuytrinh.squareplease;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

public class MainFragment extends Fragment {
  private static final int RC_PICK_PHOTO = 0;
  private final static String[] PHOTO_PROJECTION = new String[] {
      MediaStore.Images.Media._ID,
      MediaStore.Images.Media.BUCKET_ID,
      MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
      MediaStore.Images.Media.DATA
  };

  private final ViewModel viewModel = new ViewModel();
  private Subscription isBusySubscription;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRetainInstance(true);

    Intent intent = getActivity().getIntent();
    if (Intent.ACTION_SEND.equals(intent.getAction())) {
      Uri photoUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
      if (photoUri != null) {
        handlePhotoUri(photoUri);
      }
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_main, container, false);

    final View choosePhotoButton = rootView.findViewById(R.id.choosePhotoButton);
    choosePhotoButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        choosePhoto();
      }
    });

    final View progressBar = rootView.findViewById(R.id.progressBar);

    isBusySubscription = viewModel.isBusy().subscribe(new Action1<Boolean>() {
      @Override
      public void call(Boolean value) {
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        choosePhotoButton.setVisibility(value ? View.GONE : View.VISIBLE);
      }
    });

    return rootView;
  }

  @Override
  public void onDestroyView() {
    if (isBusySubscription != null) {
      isBusySubscription.unsubscribe();
    }

    super.onDestroyView();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode != Activity.RESULT_OK) {
      return;
    }

    final Uri photoUri = data.getData();
    handlePhotoUri(photoUri);
  }

  private void handlePhotoUri(Uri photoUri) {
    viewModel.makeSquare(getActivity().getApplicationContext(), photoUri)
        .subscribe(
            new Action1<Uri>() {
              @Override
              public void call(Uri squarePhotoUri) {
                sharePhoto(squarePhotoUri);
              }
            },
            new Action1<Throwable>() {
              @Override
              public void call(Throwable throwable) {
                showError(throwable);
              }
            });
  }

  private void showError(Throwable throwable) {
    if (throwable instanceof FileNotFoundException) {
      Toast.makeText(getActivity(), "Failed to load image", Toast.LENGTH_SHORT).show();
    } else {
      Toast.makeText(getActivity(), "Unexpected error", Toast.LENGTH_SHORT).show();
    }
  }

  private void sharePhoto(Uri photoUri) {
    Intent shareIntent = new Intent();
    shareIntent.setAction(Intent.ACTION_SEND);
    shareIntent.putExtra(Intent.EXTRA_STREAM, photoUri);
    shareIntent.setType("image/*");
    startActivity(shareIntent);
  }

  private void choosePhoto() {
    Intent intent = new Intent(Intent.ACTION_PICK);
    intent.setType("image/*");
    startActivityForResult(intent, RC_PICK_PHOTO);
  }

  public static class ViewModel {
    private final BehaviorSubject<Boolean> isBusy = BehaviorSubject.create(false);

    public Observable<Uri> makeSquare(final Context context, final Uri photoUri) {
      isBusy.onNext(true);
      return Observable
          .create(new Observable.OnSubscribe<Uri>() {
            @Override
            public void call(Subscriber<? super Uri> subscriber) {
              Cursor photoCursor = context.getContentResolver().query(
                  photoUri,
                  PHOTO_PROJECTION,
                  null, null, null
              );

              if (!photoCursor.moveToFirst()) {
                subscriber.onError(new FileNotFoundException());
                return;
              }

              String photoPath = photoCursor.getString(photoCursor.getColumnIndex(MediaStore.Images.Media.DATA));
              if (TextUtils.isEmpty(photoPath)) {
                subscriber.onError(new FileNotFoundException());
                return;
              }

              Bitmap originalPhoto = BitmapFactory.decodeFile(photoPath);
              int size = Math.max(originalPhoto.getWidth(), originalPhoto.getHeight());
              Bitmap squareBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

              Canvas canvas = new Canvas(squareBitmap);
              canvas.drawColor(context.getResources().getColor(android.R.color.white));

              float x = Math.abs(squareBitmap.getWidth() - originalPhoto.getWidth()) * 0.5f;
              float y = Math.abs(squareBitmap.getHeight() - originalPhoto.getHeight()) * 0.5f;
              Paint paint = new Paint();
              paint.setAntiAlias(true);
              canvas.drawBitmap(originalPhoto, x, y, paint);

              String name = String.format("square_pls_%d.png", System.currentTimeMillis());
              File squareBitmapFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
              try {
                FileOutputStream stream = new FileOutputStream(squareBitmapFile);
                squareBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.close();
              } catch (IOException e) {
                subscriber.onError(e);
                return;
              }

              subscriber.onNext(Uri.fromFile(squareBitmapFile));
              subscriber.onCompleted();
            }
          })
          .subscribeOn(Schedulers.newThread())
          .observeOn(AndroidSchedulers.mainThread())
          .finallyDo(new Action0() {
            @Override
            public void call() {
              isBusy.onNext(false);
            }
          });
    }

    public Observable<Boolean> isBusy() {
      return isBusy;
    }
  }
}