/* Copyright 2016 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gearvrf;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import android.util.Log;

import org.gearvrf.utility.DockEventReceiver;
import org.gearvrf.utility.Threads;

import java.lang.ref.WeakReference;
import java.util.HashMap;

abstract class GVRConfigurationManager {

    protected WeakReference<GVRActivity> mActivity;
    private boolean isDockListenerRequired = true;
    private boolean mResetFovY;

    protected GVRConfigurationManager(GVRActivity activity) {
        mActivity = new WeakReference<>(activity);

        mResetFovY = (0 == Float.compare(0, activity.getAppSettings().getEyeBufferParams().getFovY()));

        activity.addDockListener(new GVRActivity.DockListener() {
            @Override
            public void onDock() {
                handleOnDock();
            }

            @Override
            public void onUndock() {
            }
        });
    }

    private final Runnable mUsbCheckRunnable = new Runnable() {
        private final static int MAX_TRIES = 50;
        private int mCounter;

        @Override
        public void run() {
            final GVRActivity activity = mActivity.get();
            if (null == activity) {
                return;
            }

            SystemClock.sleep(100);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final String model = scanUsbDevicesForHeadset();
                    if (null == model) {
                        if (++mCounter <= MAX_TRIES) {
                            handleOnDock();
                        } else {
                            mCounter = 0;
                            Log.w(TAG, "too many usb checks");
                        }
                    } else {
                        mCounter = 0;
                        configureForHeadset(model);
                    }
                }
            });
        }
    };

    protected void handleOnDock() {
        Threads.spawnLow(mUsbCheckRunnable);
    }

    protected void configureForHeadset(final String model) {
        mHeadsetModel = model;
        final GVRActivity activity = mActivity.get();
        if (null == activity || !mResetFovY) {
            return;
        }

        final float fovY;
        final GVRViewManager viewManager = activity.getViewManager();

        //must determine the default fov
        if (model.contains("R323")) {
            fovY = 93;
        } else {
            fovY = 90;
        }
        Log.i(TAG, "set the default fov-y to " + fovY);

        activity.getAppSettings().getEyeBufferParams().setFovY(fovY);
        GVRPerspectiveCamera.setDefaultFovY(fovY);

        if (null != viewManager) {
            final GVRCameraRig cameraRig = viewManager.getMainScene().getMainCameraRig();
            updatePerspectiveCameraFovY(cameraRig.getLeftCamera(), fovY);
            updatePerspectiveCameraFovY(cameraRig.getRightCamera(), fovY);
            updatePerspectiveCameraFovY(cameraRig.getCenterCamera(), fovY);
        }
    }

    /**
     * For cases where the dock events are not required by the {@link GVRViewManager}, this
     * method returns a <code>false</code>
     *
     * @return <code>true</code> if the framework needs to register a listener to receive
     * dock events.
     */
    public boolean isDockListenerRequired(){
        return isDockListenerRequired;
    }

    /**
     * Set the flag to determine if the dock listener is required or not
     * @param value <code>true</code> is the dock listener is required, <code>false</code>
     *              otherwise.
     */
    void setDockListenerRequired(boolean value){
        isDockListenerRequired = value;
    }

    /**
     * @return true if GearVR is connected, false otherwise
     */
    public abstract boolean isHmtConnected();

    private String getHmtModel() {
        return mHeadsetModel;
    }

    @SuppressLint("NewApi")
    private String scanUsbDevicesForHeadset() {
        final GVRActivity activity = mActivity.get();
        if (null == activity) {
            return null;
        }

        final int vendorId = 1256;
        final int productId = 42240;

        final UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        final HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (final UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                return device.getSerialNumber();
            }
        }

        return null;
    }

    /**
     * Does the headset the device is docked into have a dedicated home key
     * @return
     */
    public boolean isHomeKeyPresent() {
        final GVRActivity activity = mActivity.get();
        if (null != activity) {
            final String model = getHmtModel();
            if (null != model && model.contains("R323")) {
                return true;
            }
        }
        return false;
    }

    DockEventReceiver makeDockEventReceiver(final Activity gvrActivity, final Runnable runOnDock,
                                            final Runnable runOnUndock) {
        return new DockEventReceiver(gvrActivity, runOnDock, runOnUndock);
    }

    private void updatePerspectiveCameraFovY(final GVRCamera camera, final float fovY) {
        if (camera instanceof GVRPerspectiveCamera) {
            ((GVRPerspectiveCamera) camera).setFovY(fovY);
        }
    }

    private String mHeadsetModel;
    static final String DEFAULT_HEADSET_MODEL = "R322";
    private static final String TAG = "GVRConfigurationManager";
}
