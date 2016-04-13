package net.hockeyapp.android;

import android.text.TextUtils;
import net.hockeyapp.android.objects.CrashDetails;
import net.hockeyapp.android.utils.HockeyLog;

import java.io.*;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Date;
import java.util.UUID;

/**
 * <h3>Description</h3>
 * Helper class to catch exceptions. Saves the stack trace
 * as a file and executes callback methods to ask the app for
 * additional information and meta data (see CrashManagerListener).

 * <h3>License</h3>
 * <pre>
 * Copyright (c) 2009 nullwire aps
 * Copyright (c) 2011-2014 Bit Stadium GmbH
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * </pre>
 *
 * @author Mads Kristiansen
 * @author Glen Humphrey
 * @author Evan Charlton
 * @author Peter Hewitt
 * @author Thomas Dohmke
 * @author Matthias Wenz
 * @author Benjamin Reimold
 **/
public class ExceptionHandler implements UncaughtExceptionHandler {
    private boolean mIgnoreDefaultHandler = false;
    private CrashManagerListener mCrashManagerListener;
    private UncaughtExceptionHandler mDefaultExceptionHandler;

    public ExceptionHandler(UncaughtExceptionHandler defaultExceptionHandler, CrashManagerListener listener, boolean ignoreDefaultHandler) {
        mDefaultExceptionHandler = defaultExceptionHandler;
        mIgnoreDefaultHandler = ignoreDefaultHandler;
        mCrashManagerListener = listener;
    }

    public void setListener(CrashManagerListener listener) {
        mCrashManagerListener = listener;
    }

    /**
     * Save a caught exception to disk.
     *
     * @param exception Exception to save.
     * @param listener  Custom CrashManager listener instance.
     * @deprecated in 3.7.0-beta.2. Use saveException(Throwable exception, Thread thread,
     * CrashManagerListener listener) instead.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static void saveException(Throwable exception, CrashManagerListener listener) {
        saveException(exception, null, listener);
    }

    /**
     * Save a caught exception to disk.
     *
     * @param exception Exception to save.
     * @param thread    Thread that crashed.
     * @param listener  Custom CrashManager listener instance.
     */
    public static void saveException(Throwable exception, Thread thread, CrashManagerListener listener) {
        saveException(exception, thread, null, listener);
    }

    public static void saveException(Throwable exception, Thread thread, String managedExceptionString , CrashManagerListener listener) {
        final Date now = new Date();
        final Date startDate = new Date(CrashManager.getInitializeTimestamp());
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        exception.printStackTrace(printWriter);

        managedExceptionString = "System.Runtime.ExceptionServices.ExceptionDispatchInfo.Throw() Android.Runtime.JNIEnv.CallVoidMethod(IntPtr jobject, IntPtr jmethod, JValue* parms) Com.Microsoft.AI.Xamarinexample.ExampleClass.ForceAppCrash(Activity p0) XamarinTest.Droid.DummyLibraryAndroid.TriggerExceptionCrash() XamarinTest.DummyLibrary.TriggerExceptionCrash() XamarinTest.XamarinTestMasterView.TrackTelemetryData(TelemetryType type) XamarinTest.XamarinTestMasterView.<XamarinTestMasterView>m__3() at Xamarin.Forms.Command+<>c__DisplayClass2.<.ctor>b__0 (System.Object o) <0x9b13fb68 + 0x00014> in <filename unknown>:0 Xamarin.Forms.Command.Execute(object parameter) Xamarin.Forms.TextCell.OnTapped() Xamarin.Forms.TableView.TableSectionModel.OnRowSelected(object item) Xamarin.Forms.TableModel.RowSelected(object item) Xamarin.Forms.TableModel.RowSelected(int section, int row) Xamarin.Forms.Platform.Android.TableViewModelRenderer.HandleItemClick(AdapterView parent, View nview, int position, long id) Xamarin.Forms.Platform.Android.CellAdapter.OnItemClick(AdapterView parent, View view, int position, long id) Android.Widget.AdapterView.IOnItemClickListenerInvoker.n_OnItemClick_Landroid_widget_AdapterView_Landroid_view_View_IJ(IntPtr jnienv, IntPtr native__this, IntPtr native_parent, IntPtr native_view, int position, long id) at (wrapper dynamic-method) System.Object:ab525826-8008-474b-a02c-b5ae8ba471a3 (intptr,intptr,intptr,intptr,int,long)";

        String filename = UUID.randomUUID().toString();

        CrashDetails crashDetails = new CrashDetails(filename, exception);
        crashDetails.setAppPackage(Constants.APP_PACKAGE);
        crashDetails.setAppVersionCode(Constants.APP_VERSION);
        crashDetails.setAppVersionName(Constants.APP_VERSION_NAME);
        crashDetails.setAppStartDate(startDate);
        crashDetails.setAppCrashDate(now);
        crashDetails.setManagedExceptionString(managedExceptionString);

        if ((listener == null) || (listener.includeDeviceData())) {
            crashDetails.setOsVersion(Constants.ANDROID_VERSION);
            crashDetails.setOsBuild(Constants.ANDROID_BUILD);
            crashDetails.setDeviceManufacturer(Constants.PHONE_MANUFACTURER);
            crashDetails.setDeviceModel(Constants.PHONE_MODEL);
        }

        if (thread != null && ((listener == null) || (listener.includeThreadDetails()))) {
            crashDetails.setThreadName(thread.getName() + "-" + thread.getId());
        }

        if (Constants.CRASH_IDENTIFIER != null && (listener == null || listener.includeDeviceIdentifier())) {
            crashDetails.setReporterKey(Constants.CRASH_IDENTIFIER);
        }

        crashDetails.writeCrashReport();

        if (listener != null) {
            try {
                writeValueToFile(limitedString(listener.getUserID()), filename + ".user");
                writeValueToFile(limitedString(listener.getContact()), filename + ".contact");
                writeValueToFile(listener.getDescription(), filename + ".description");
            } catch (IOException e) {
                HockeyLog.error("Error saving crash meta data!", e);
            }

        }
    }

    public void uncaughtException(Thread thread, Throwable exception) {

        PrivateEventManager.postEvent(new PrivateEventManager.Event(PrivateEventManager.EVENT_TYPE_UNCAUGHT_EXCEPTION));

        if (Constants.FILES_PATH == null) {
            // If the files path is null, the exception can't be stored
            // Always call the default handler instead
            mDefaultExceptionHandler.uncaughtException(thread, exception);
        } else {
            saveException(exception, thread, mCrashManagerListener);

            if (!mIgnoreDefaultHandler) {
                mDefaultExceptionHandler.uncaughtException(thread, exception);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        }
    }

    private static void writeValueToFile(String value, String filename) throws IOException {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        BufferedWriter writer = null;
        try {
            String path = Constants.FILES_PATH + "/" + filename;
            if (!TextUtils.isEmpty(value) && TextUtils.getTrimmedLength(value) > 0) {
                writer = new BufferedWriter(new FileWriter(path));
                writer.write(value);
                writer.flush();
            }
        } catch (IOException e) {
            // TODO: Handle exception here
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static String limitedString(String string) {
        if (!TextUtils.isEmpty(string) && string.length() > 255) {
            string = string.substring(0, 255);
        }
        return string;
    }
}
