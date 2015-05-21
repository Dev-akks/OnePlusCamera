package com.oneplus.camera.ui;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v4.view.ViewPager.PageTransformer;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.oneplus.base.EventArgs;
import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.HandlerUtils;
import com.oneplus.base.Log;
import com.oneplus.base.Rotation;
import com.oneplus.base.component.ComponentSearchCallback;
import com.oneplus.base.component.ComponentUtils;
import com.oneplus.camera.CameraActivity;
import com.oneplus.camera.R;
import com.oneplus.camera.UIComponent;
import com.oneplus.camera.io.FileManager;
import com.oneplus.camera.io.FileManager.PhotoCallback;
import com.oneplus.camera.widget.RotateRelativeLayout;

final class PreviewGallery extends UIComponent
{
	// Constants
	static private final int MESSAGE_UPDATE_RESET = 1000;
	static private final int MESSAGE_UPDATE_ADDED = 1001;
	static private final int MESSAGE_UPDATE_DELETED = 1002;
	
	// Private fields
	private RotateRelativeLayout m_PreviewGallery;
	private ViewPager m_ViewPager;
	private VerticalViewPager m_VerticalViewPager;
	private PagerAdapter m_Adapter, m_VerticalAdapter;
	private FileManager m_FileManager;
	private	int	m_OrignalZ;

	// Constructor
	PreviewGallery(CameraActivity cameraActivity) {
		super("Preview Gallery", cameraActivity, true);
	}

	// Handle message.
	@Override
	protected void handleMessage(Message msg) {
		if(m_ViewPager == null)
			return;
		switch (msg.what) {
		case MESSAGE_UPDATE_DELETED:{
			File file = (File)(msg.obj);
			m_ViewPager.setAdapter(null);
			m_VerticalViewPager.setAdapter(null);
			m_Adapter.deleteFile(file);
			m_VerticalAdapter.deleteFile(file);
			m_ViewPager.setAdapter(m_Adapter);
			m_VerticalViewPager.setAdapter(m_VerticalAdapter);
			bringToBack();
			break;
		}
		case MESSAGE_UPDATE_RESET:{
			m_ViewPager.setAdapter(null);
			m_VerticalViewPager.setAdapter(null);
			m_Adapter.initialize(m_FileManager, PreviewGallery.this);
			m_VerticalAdapter.initialize(m_FileManager, PreviewGallery.this);
			m_ViewPager.setAdapter(m_Adapter);
			m_VerticalViewPager.setAdapter(m_VerticalAdapter);
			bringToBack();
			break;
		}
		case MESSAGE_UPDATE_ADDED:{
			int current = m_ViewPager.getCurrentItem();
			m_ViewPager.setAdapter(null);
			m_VerticalViewPager.setAdapter(null);
			m_Adapter.initialize(m_FileManager, PreviewGallery.this);
			m_VerticalAdapter.initialize(m_FileManager, PreviewGallery.this);
			m_ViewPager.setAdapter(m_Adapter);
			m_VerticalViewPager.setAdapter(m_VerticalAdapter);
			if(current != 0){
				m_ViewPager.setCurrentItem(current+1);
				m_VerticalViewPager.setCurrentItem(current+1);
			}
			break;
		}
		default:
			super.handleMessage(msg);
			break;
		}
	}

	// Initialize.
	@Override
	protected void onInitialize() {
		// call super
		super.onInitialize();

		// setup UI
		final CameraActivity cameraActivity = getCameraActivity();
		m_PreviewGallery = (RotateRelativeLayout)cameraActivity.findViewById(R.id.preview_gallery);

		ViewGroup parent = ((ViewGroup) m_PreviewGallery.getParent());
		for (int index = 0; index < parent.getChildCount(); index++) {
			if (parent.getChildAt(index).getId() == R.id.preview_gallery) {
				m_OrignalZ = index;
			}
		}

		initPager(getCameraActivity());

	}
	
	/* 
	 * @see com.oneplus.camera.UIComponent#onRotationChanged(com.oneplus.base.Rotation, com.oneplus.base.Rotation)
	 */
	@Override
	protected void onRotationChanged(Rotation prevRotation, Rotation newRotation) {
		super.onRotationChanged(prevRotation, newRotation);
		
		if(Rotation.PORTRAIT == newRotation || Rotation.INVERSE_PORTRAIT == newRotation){
			m_VerticalViewPager.setVisibility(View.INVISIBLE);
			m_ViewPager.setVisibility(View.VISIBLE);
			m_ViewPager.setCurrentItem(m_VerticalViewPager.getCurrentItem());
			m_PreviewGallery.setRotation(newRotation);
		}else{
			m_ViewPager.setVisibility(View.INVISIBLE);
			m_VerticalViewPager.setVisibility(View.VISIBLE);
			m_VerticalViewPager.setCurrentItem(m_ViewPager.getCurrentItem());
		}
	}

	void initPager(final CameraActivity cameraActivity) {
		initPortrait(cameraActivity);
		initLandscape(cameraActivity);
		if (Rotation.PORTRAIT == getRotation() || Rotation.INVERSE_PORTRAIT == getRotation()) {
			m_VerticalViewPager.setVisibility(View.INVISIBLE);
			m_ViewPager.setVisibility(View.VISIBLE);		
		} else {
			m_ViewPager.setVisibility(View.INVISIBLE);
			m_VerticalViewPager.setVisibility(View.VISIBLE);
		}
	}
	
	void initPortrait(final CameraActivity cameraActivity) {
		m_ViewPager = (ViewPager) m_PreviewGallery.findViewById(R.id.preview_gallery_pager);
		m_ViewPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
		m_ViewPager.setOffscreenPageLimit(3);
		m_Adapter = new PagerAdapter(cameraActivity.getFragmentManager());

		// find components
		ComponentUtils.findComponent(getCameraThread(), FileManager.class, this, new ComponentSearchCallback<FileManager>() {

			@Override
			public void onComponentFound(FileManager component) {
				Log.d(TAG, "onComponentFound");
				m_FileManager = component;
				HandlerUtils.post(m_FileManager, new Runnable() {

					@Override
					public void run() {
						m_FileManager.addHandler(FileManager.EVENT_MEDIA_FILES_RESET, new EventHandler<EventArgs>() {

							@Override
							public void onEventReceived(EventSource source, EventKey<EventArgs> key, EventArgs e) {
								HandlerUtils.sendMessage(PreviewGallery.this, MESSAGE_UPDATE_RESET);

							}

						});

						m_FileManager.addHandler(FileManager.EVENT_MEDIA_FILES_ADDED, new EventHandler<EventArgs>() {

							@Override
							public void onEventReceived(EventSource source, EventKey<EventArgs> key, EventArgs e) {
								HandlerUtils.sendMessage(PreviewGallery.this, MESSAGE_UPDATE_ADDED);

							}

						});

					}
				});

				m_Adapter.initialize(m_FileManager, PreviewGallery.this);
			}
		});
		m_ViewPager.setAdapter(m_Adapter);
		m_ViewPager.setOnPageChangeListener(new OnPageChangeListener() {

			@Override
			public void onPageScrollStateChanged(int state) {

			}

			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			}

			@Override
			public void onPageSelected(int position) {
				if (position == 0) {
					bringToBack();
				} else {
					bringToFront();
				}
			}
		});

		m_ViewPager.setPageTransformer(false, new PageTransformer() {

			@Override
			public void transformPage(View view, float position) {
				final float MIN_SCALE = 0.85f;
				final float MIN_ALPHA = 0.6f;
				int pageWidth = view.getWidth();
				int pageHeight = view.getHeight();

				if (position < -1) { // [-Infinity,-1)
					// This page is way off-screen to the left.
					view.setAlpha(0);

				} else if (position <= 1) { // [-1,1]
					// Modify the default slide transition to shrink the page as
					// well
					float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
					float vertMargin = pageHeight * (1 - scaleFactor) / 2;
					float horzMargin = pageWidth * (1 - scaleFactor) / 2;
					if (position < 0) {
						view.setTranslationX(horzMargin - vertMargin / 2);
					} else {
						if (m_ViewPager.getCurrentItem() == 0) {
							horzMargin *= 3.3;
						} else {
							horzMargin *= 4;
						}
						view.setTranslationX(-horzMargin + vertMargin / 2);
					}

					// Scale the page down (between MIN_SCALE and 1)
					view.setScaleX(scaleFactor);
					view.setScaleY(scaleFactor);

					// Fade the page relative to its size.
					view.setAlpha(MIN_ALPHA + (scaleFactor - MIN_SCALE) / (1 - MIN_SCALE) * (1 - MIN_ALPHA));

				} else { // (1,+Infinity]
					// This page is way off-screen to the right.
					view.setAlpha(0);
				}

			}
		});

		m_ViewPager.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(m_ViewPager.getCurrentItem()==0){
					cameraActivity.onTouchEvent(event);
				}
				return false;
			}
		});
	}

	void initLandscape(final CameraActivity cameraActivity) {
		m_VerticalViewPager = (VerticalViewPager) m_PreviewGallery.findViewById(R.id.preview_gallery_pager_landscape);
		m_VerticalViewPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
		m_VerticalViewPager.setOffscreenPageLimit(3);
		m_VerticalAdapter = new PagerAdapter(cameraActivity.getFragmentManager());

		// find components
		ComponentUtils.findComponent(getCameraThread(), FileManager.class, this, new ComponentSearchCallback<FileManager>() {

			@Override
			public void onComponentFound(FileManager component) {
				Log.d(TAG, "onComponentFound");
				m_FileManager = component;
				HandlerUtils.post(m_FileManager, new Runnable() {

					@Override
					public void run() {
						m_FileManager.addHandler(FileManager.EVENT_MEDIA_FILES_RESET, new EventHandler<EventArgs>() {

							@Override
							public void onEventReceived(EventSource source, EventKey<EventArgs> key, EventArgs e) {
								HandlerUtils.sendMessage(PreviewGallery.this, MESSAGE_UPDATE_RESET);

							}

						});

						m_FileManager.addHandler(FileManager.EVENT_MEDIA_FILES_ADDED, new EventHandler<EventArgs>() {

							@Override
							public void onEventReceived(EventSource source, EventKey<EventArgs> key, EventArgs e) {
								HandlerUtils.sendMessage(PreviewGallery.this, MESSAGE_UPDATE_ADDED);

							}

						});

					}
				});

				m_VerticalAdapter.initialize(m_FileManager, PreviewGallery.this);
			}
		});
		m_VerticalViewPager.setAdapter(m_VerticalAdapter);
		m_VerticalViewPager.setOnPageChangeListener(new OnPageChangeListener() {

			@Override
			public void onPageScrollStateChanged(int state) {

			}

			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			}

			@Override
			public void onPageSelected(int position) {
				if (position == 0) {
					bringToBack();
				} else {
					bringToFront();
				}
			}
		});
		
		m_VerticalViewPager.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(m_VerticalViewPager.getCurrentItem()==0){
					cameraActivity.onTouchEvent(event);
				}
				return false;
			}
		});
	}
	
	void bringToBack(){
		m_PreviewGallery.setBackgroundDrawable(null);
		ViewGroup parent = ((ViewGroup) m_PreviewGallery.getParent());
		parent.removeView(m_PreviewGallery);
		parent.addView(m_PreviewGallery, m_OrignalZ);
	}
	
	void bringToFront() {
		m_PreviewGallery
				.setBackgroundColor(getCameraActivity().getResources().getColor(R.color.Previerw_gallery_background));
		m_PreviewGallery.bringToFront();
	}

	private static class ImageFragment extends Fragment {

		private File m_File;
		private FileManager m_FileManager;
		private PreviewGallery m_Gallery;
		static private final String TAG = ImageFragment.class.getSimpleName();

		public ImageFragment(File file, FileManager fileManager, PreviewGallery gallery) {
			m_File = file;
			m_FileManager = fileManager;
			m_Gallery = gallery;
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			Log.d(TAG, "onCreate");
			super.onCreate(savedInstanceState);

		}

		@Override
		public void onDestroyView() {
			Log.d(TAG, "onDestroyView");
			super.onDestroyView();
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.layout_preview_gallery_item, container, false);
			final ImageView image = (ImageView) (view.findViewById(R.id.preview_image));
			Resources res = inflater.getContext().getResources();
			int reqWidth = res.getDimensionPixelSize(R.dimen.preview_item_width);
			int reqHeight = res.getDimensionPixelSize(R.dimen.preview_item_height);

			m_FileManager.getBitmap(m_File.getAbsolutePath(), reqWidth, reqHeight, new PhotoCallback() {

				@Override
				public void onBitmapLoad(final Bitmap bitmap) {
					if (bitmap != null) {
						new Handler(Looper.getMainLooper()).post(new Runnable() {

							@Override
							public void run() {
								image.setScaleType(ImageView.ScaleType.FIT_CENTER);
								image.setImageBitmap(bitmap);
								image.setOnClickListener(new View.OnClickListener() {

									@Override
									public void onClick(View v) {
										Intent intent = new Intent();
										intent.setAction(Intent.ACTION_VIEW);
										intent.setDataAndType(Uri.fromFile(m_File), "image/*");
										startActivity(intent);
									}
								});
							}
						});
					}else{
						Handler hr = new Handler(Looper.getMainLooper());
//						hr.sendMessage(Message.obtain(hr, MESSAGE_UPDATE_DELETED, m_File));
						HandlerUtils.sendMessage(m_Gallery, MESSAGE_UPDATE_DELETED, 0, 0, m_File);
						
					}

				}
			});
			return view;
		}
	}
	
    private static class PagerAdapter extends FragmentStatePagerAdapter {
    	private List<File> m_Files;
    	private FileManager m_FileManager;
    	private PreviewGallery m_PreviewGallery;

        public PagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }
        
        void initialize(FileManager fileManager, PreviewGallery gallery){
        	m_FileManager = fileManager;
        	m_Files = fileManager.getMediaFiles();
        	m_PreviewGallery = gallery;
        }
        
        void deleteFile(File file){
        	Iterator<File> it = m_Files.iterator();
        	File fileItem;
        	while(it.hasNext()){
        		fileItem = it.next();
        		if(fileItem.getAbsoluteFile().equals(file.getAbsoluteFile())){
        			it.remove();
        		}
        	}
        	notifyDataSetChanged();
        }

        // Returns total number of pages
        @Override
        public int getCount() {
            return m_Files.size() + 1;
        }

        // Returns the fragment to display for that page
        @Override
        public Fragment getItem(int position) {
            switch (position) {
            case 0: // Fragment # 0 - This will show FirstFragment
                return new Fragment();
            default:
                return new ImageFragment(m_Files.get(position - 1), m_FileManager, m_PreviewGallery);
            }
        }

        // Returns the page title for the top indicator
        @Override
        public CharSequence getPageTitle(int position) {
            return "Page " + position;
        }

    }
}
