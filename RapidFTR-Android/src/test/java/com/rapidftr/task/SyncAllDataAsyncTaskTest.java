package com.rapidftr.task;

import android.app.Notification;
import android.app.NotificationManager;
import android.view.Menu;
import android.view.MenuItem;
import com.rapidftr.CustomTestRunner;
import com.rapidftr.R;
import com.rapidftr.RapidFtrApplication;
import com.rapidftr.activity.RapidFtrActivity;
import com.rapidftr.model.Child;
import com.rapidftr.model.User;
import com.rapidftr.repository.ChildRepository;
import com.rapidftr.service.ChildSyncService;
import com.rapidftr.service.DeviceService;
import com.rapidftr.service.FormService;
import com.rapidftr.utils.http.FluentRequest;
import com.xtremelabs.robolectric.Robolectric;
import com.xtremelabs.robolectric.shadows.ShadowToast;
import org.apache.http.HttpException;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Matchers;
import org.mockito.Mock;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(CustomTestRunner.class)
public class SyncAllDataAsyncTaskTest {

    @Mock private ChildSyncService childSyncService;
    @Mock private ChildRepository childRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private RapidFtrActivity rapidFtrActivity;
    @Mock private NotificationManager notificationManager;
    @Mock private Menu menu;
    @Mock private MenuItem syncAll;
    @Mock private MenuItem cancelSyncAll;
    @Mock private User currentUser;
    @Mock private FormService formService;
    @Mock private DeviceService deviceService;

    private RapidFtrApplication application;

    private SyncAllDataAsyncTask syncAllDataAsyncTask;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        doReturn(syncAll).when(menu).getItem(0);
        doReturn(cancelSyncAll).when(menu).getItem(1);
        doReturn(menu).when(rapidFtrActivity).getMenu();

        given(rapidFtrActivity.getSystemService(Matchers.<String>any())).willReturn(notificationManager);

        application = spy(RapidFtrApplication.getApplicationInstance());

        syncAllDataAsyncTask = new SyncAllDataAsyncTask(formService, childSyncService, deviceService, childRepository, currentUser);
    }

    @Test
    public void shouldSyncFormsAndChildren() throws Exception {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        given(childRepository.toBeSynced()).willReturn(newArrayList(child1, child2));
        syncAllDataAsyncTask.setContext(rapidFtrActivity);

        syncAllDataAsyncTask.execute();
        verify(formService).getPublishedFormSections();
        verify(childSyncService).sync(child1, currentUser);
        verify(childSyncService).sync(child2, currentUser);
    }

    @Test
    public void shouldNotSyncFormsIfTaskIsCancelled() throws Exception {
        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        syncAllDataAsyncTask = spy(syncAllDataAsyncTask);
        doReturn(true).when(syncAllDataAsyncTask).isCancelled();

        syncAllDataAsyncTask.doInBackground();

        verify(formService, never()).getPublishedFormSections();
    }

    @Test
    public void shouldNotSyncChildrenIfCancelled() throws Exception {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        given(childRepository.toBeSynced()).willReturn(newArrayList(child1, child2));

        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        syncAllDataAsyncTask = spy(syncAllDataAsyncTask);
        doReturn(true).when(syncAllDataAsyncTask).isCancelled();

        syncAllDataAsyncTask.onPreExecute();
        syncAllDataAsyncTask.doInBackground();
        verify(childSyncService, never()).sync(child1, currentUser);
        verify(childSyncService, never()).sync(child2, currentUser);
    }

    @Test
    public void shouldNotGetIncomingChildrenFromServerIfCancelled() throws Exception {
        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        HashMap<String, String> repositoryIDRevs = createRepositoryIdRevMap();

        given(childSyncService.getIdsToDownload()).willReturn(Arrays.asList("asd97"));
        given(childRepository.getAllIdsAndRevs()).willReturn(repositoryIDRevs);

        syncAllDataAsyncTask = spy(syncAllDataAsyncTask);
        doReturn(true).when(syncAllDataAsyncTask).isCancelled();

        syncAllDataAsyncTask.onPreExecute();
        syncAllDataAsyncTask.doInBackground();

        verify(childSyncService).getRecord(any(String.class));
        verify(childRepository, never()).createOrUpdate((Child) any());
        verify(childSyncService, never()).setMedia((Child) any());
    }

    @Test
    public void shouldNotGetIncomingChildrenFromServerIfBlacklisted() throws Exception {
        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        Child child1 = mock(Child.class);
        ArrayList<Child> childList = new ArrayList<Child>();
        childList.add(child1);

        given(childRepository.toBeSynced()).willReturn(childList);
        given(deviceService.isBlacklisted()).willReturn(true);

        syncAllDataAsyncTask.execute();

        verify(childSyncService).sync(child1, currentUser);
        verify(childSyncService, never()).getRecord(any(String.class));
        verify(childSyncService, never()).getIdsToDownload();
    }

    @Test
    public void shouldCreateOrUpdateExistingChild() throws Exception {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        HashMap<String, String> repositoryIDRevs = createRepositoryIdRevMap();

        given(childSyncService.getIdsToDownload()).willReturn(Arrays.asList("qwerty0987","abcd1234"));
        given(childRepository.getAllIdsAndRevs()).willReturn(repositoryIDRevs);
        given(child1.getUniqueId()).willReturn("1234");
        given(child2.getUniqueId()).willReturn("5678");

        given(childSyncService.getRecord("qwerty0987")).willReturn(child1);
        given(childSyncService.getRecord("abcd1234")).willReturn(child2);

        given(childRepository.exists("1234")).willReturn(true);
        given(childRepository.exists("5678")).willReturn(false);

        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        syncAllDataAsyncTask.execute();

        verify(childSyncService).getRecord("qwerty0987");
        verify(childRepository).update(child1);
        verify(childRepository).createOrUpdate(child2);
    }

    @Test
    public void shouldToggleMenuOnPreExecute(){
        syncAllDataAsyncTask.setContext(rapidFtrActivity);

        syncAllDataAsyncTask.onPreExecute();

        verify(syncAll).setVisible(false);
        verify(cancelSyncAll).setVisible(true);
    }

    @Test
    public void shouldToggleMenuOnCancelAndOnPostExecute(){
        syncAllDataAsyncTask.setContext(rapidFtrActivity);

        syncAllDataAsyncTask.onPreExecute();

        syncAllDataAsyncTask.onCancelled();
        verify(syncAll).setVisible(true);
        verify(cancelSyncAll).setVisible(false);

        syncAllDataAsyncTask.onPreExecute();
        verify(syncAll).setVisible(true);
        verify(cancelSyncAll).setVisible(false);
    }

    @Test
    public void shouldNotCallSetProgressAndNotifyIfCancelled(){
        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        syncAllDataAsyncTask = spy(syncAllDataAsyncTask);

        doReturn(true).when(syncAllDataAsyncTask).isCancelled();

        syncAllDataAsyncTask.onPreExecute();
        verify(notificationManager, never()).notify(anyInt(), (Notification) anyObject());
    }

	@Test
	public void shouldShowSessionTimeoutMessage() throws JSONException, IOException {
		Robolectric.getFakeHttpLayer().setDefaultHttpResponse(401, "Unauthorized");
		given(rapidFtrActivity.getString(R.string.session_timeout)).willReturn("Your session is timed out");
		syncAllDataAsyncTask.recordSyncService = new ChildSyncService(RapidFtrApplication.getApplicationInstance(), childRepository, new FluentRequest());
		syncAllDataAsyncTask.setContext(rapidFtrActivity);
		syncAllDataAsyncTask.execute();

		assertThat(ShadowToast.getTextOfLatestToast(), equalTo("Your session is timed out"));
	}

    @Test
    public void shouldCompareAndRetrieveIdsToBeDownloadedFromServer() throws JSONException, IOException, HttpException {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        given(childRepository.toBeSynced()).willReturn(newArrayList(child1, child2));
        given(childSyncService.getIdsToDownload()).willReturn(Arrays.asList("qwerty0987", "abcd1234"));
        given(childSyncService.getRecord("qwerty0987")).willReturn(mock(Child.class));
        given(childSyncService.getRecord("abcd1234")).willReturn(mock(Child.class));

        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        syncAllDataAsyncTask.execute();

        verify(formService).getPublishedFormSections();
        verify(childSyncService).sync(child1, currentUser);
        verify(childSyncService).sync(child2, currentUser);
        verify(childSyncService).getIdsToDownload();
        verify(childSyncService).getRecord("qwerty0987");
        verify(childSyncService).getRecord("abcd1234");
    }

    @Test
    public void shouldWipeDeviceIfItIsBlacklisted() throws IOException, JSONException {
        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        ArrayList<Child> childList = new ArrayList<Child>();

        given(childRepository.toBeSynced()).willReturn(childList);
        given(deviceService.isBlacklisted()).willReturn(true);
        doNothing().when(deviceService).wipeData();

        syncAllDataAsyncTask.execute();
        verify(deviceService).wipeData();
    }

    @Test
    public void shouldNotWipeDeviceIfChildRecordsArePending() throws JSONException, IOException {
        syncAllDataAsyncTask.setContext(rapidFtrActivity);
        ArrayList<Child> childList = new ArrayList<Child>();
        Child child = mock(Child.class);
        childList.add(child);
        given(childRepository.toBeSynced()).willReturn(childList);
        given(deviceService.isBlacklisted()).willReturn(true);

        syncAllDataAsyncTask.execute();
        verify(childRepository, times(2)).toBeSynced();
        verify(deviceService, never()).wipeData();
    }

    private HashMap<String, String> createRepositoryIdRevMap() {
        HashMap<String, String> repositoryIDRevs = new HashMap<String, String>();
        repositoryIDRevs.put("abcd1234", "1-zxy321");
        repositoryIDRevs.put("abcd5678", "2-zxy765");
        repositoryIDRevs.put("abcd7689", "3-cdsf76");
        return repositoryIDRevs;
    }
}
