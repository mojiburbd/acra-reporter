package nz.org.winters.appspot.acrareporter.client.ui;

/*
 * Copyright 2013 Mathew Winters

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import nz.org.winters.appspot.acrareporter.client.RemoteDataService;
import nz.org.winters.appspot.acrareporter.client.RemoteDataServiceAsync;
import nz.org.winters.appspot.acrareporter.client.ui.images.Resources;
import nz.org.winters.appspot.acrareporter.shared.AppPackageShared;
import nz.org.winters.appspot.acrareporter.shared.BasicErrorInfoShared;
import nz.org.winters.appspot.acrareporter.shared.LoginInfo;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.i18n.client.Constants;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.ColumnSortEvent.ListHandler;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.cellview.client.RowStyles;
import com.google.gwt.user.cellview.client.SimplePager;
import com.google.gwt.user.cellview.client.SimplePager.TextLocation;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.DefaultSelectionEventManager;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.MultiSelectionModel;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;
import com.google.gwt.view.client.SelectionModel;
import com.google.gwt.view.client.SingleSelectionModel;

public class MainErrorsList extends Composite implements Handler
{
  public interface CallbackShowReport{
    public void showReport(BasicErrorInfoShared basicErrorInfo);
  }

  private UIConstants                                   constants     = (UIConstants) GWT.create(UIConstants.class);

  public static final ProvidesKey<BasicErrorInfoShared> KEY_PROVIDER  = new ProvidesKey<BasicErrorInfoShared>()
                                                                      {
                                                                        @Override
                                                                        public Object getKey(BasicErrorInfoShared item)
                                                                        {
                                                                          return item == null ? null : item.id;
                                                                        }
                                                                      };
  @UiField(provided = true)
  SimplePager                                           simplePager;

  @UiField
  DataGrid<BasicErrorInfoShared>                        dataGrid;

  @UiField
  HorizontalPanel                                       checkMultiSelect;
  @UiField
  MenuItem                                              popupActions;
  @UiField
  MenuItem                                              miErrorsAllLookedAt;
  @UiField
  MenuItem                                              miErrorsAllFixed;
  @UiField
  MenuItem                                              miErrorsAllEMailed;
  @UiField
  MenuItem                                              miReportsEMailSelected;
  @UiField
  MenuItem                                              miReload;
  @UiField
  MenuItem                                              miErrorsDeleteSelected;
  @UiField
  CheckBox                                              checkErrorsMultiSelect;

  private final RemoteDataServiceAsync                  remoteService = GWT.create(RemoteDataService.class);

  private ListHandler<BasicErrorInfoShared>             sortHandler;
  private SingleSelectionModel<BasicErrorInfoShared>    singleSelectionModel;
  private MultiSelectionModel<BasicErrorInfoShared>     multipleSelectionModel;

  private static MainErrorsListUiBinder                 uiBinder      = GWT.create(MainErrorsListUiBinder.class);
  private ListProvider                                  dataProvider  = new ListProvider();

  private CallbackShowReport mCallbackShowReport ;

  private String                                        packageName;

  private LoginInfo mLoginInfo;

  private AppPackageShared mAppPackageShared;

  interface MainErrorsListUiBinder extends UiBinder<Widget, MainErrorsList>
  {
  }

  public MainErrorsList(CallbackShowReport callback, LoginInfo loginInfo, AppPackageShared appPackageShared)
  {
    mLoginInfo = loginInfo;
    mAppPackageShared = appPackageShared;
    mCallbackShowReport = callback;
    init();
  }

  private void init()
  {
    SimplePager.Resources pagerResources = GWT.create(SimplePager.Resources.class);
    simplePager = new SimplePager(TextLocation.CENTER, pagerResources, false, 0, true);
    initWidget(uiBinder.createAndBindUi(this));

    // dataGrid = new DataGrid<BasicErrorInfoShared>();
    // dataGrid.setWidth("100%");
    dataGrid.setAutoHeaderRefreshDisabled(true);
    dataGrid.setEmptyTableWidget(new Label(constants.gridEmpty()));

    sortHandler = new ListHandler<BasicErrorInfoShared>(dataProvider.getList());
    dataGrid.addColumnSortHandler(sortHandler);
    dataGrid.setRowStyles(mGridRowStyles);

    simplePager.setDisplay(dataGrid);

    singleSelectionModel = new SingleSelectionModel<BasicErrorInfoShared>(KEY_PROVIDER);
    multipleSelectionModel = new MultiSelectionModel<BasicErrorInfoShared>(KEY_PROVIDER);

    dataGrid.setSelectionModel(singleSelectionModel, DefaultSelectionEventManager.<BasicErrorInfoShared> createDefaultManager());

    initTableColumns(singleSelectionModel, sortHandler);

    dataProvider.setList(new ArrayList<BasicErrorInfoShared>());
    sortHandler.setList(dataProvider.getList());
    dataProvider.addDataDisplay(dataGrid);

    dataGrid.getColumnSortList().clear();
    dataGrid.getColumnSortList().push(dataGrid.getColumn(0));
    dataGrid.getColumnSortList().push(dataGrid.getColumn(0));

    dataGrid.setLoadingIndicator(new Image(Resources.INSTANCE.loaderImage()));

    singleSelectionModel.addSelectionChangeHandler(this);
    setupMenus();

  }

  void startLoading()
  {

    AppLoadingView.getInstance().start();
  }

  void stopLoading()
  {
    AppLoadingView.getInstance().stop();
  }

  private void setupMenus()
  {
    popupActions.setEnabled(false);
    miErrorsAllEMailed.setScheduledCommand(new Command()
    {

      @Override
      public void execute()
      {
        startLoading();
        final Set<BasicErrorInfoShared> selected = multipleSelectionModel.getSelectedSet();
        Iterator<BasicErrorInfoShared> iter = selected.iterator();
        ArrayList<String> reportIds = new ArrayList<String>();
        while (iter.hasNext())
        {
          reportIds.add(iter.next().REPORT_ID);
        }
        remoteService.markReportsEMailed(reportIds, true, new AsyncCallback<Void>()
        {

          @Override
          public void onSuccess(Void result)
          {
            Iterator<BasicErrorInfoShared> iter = selected.iterator();
            while (iter.hasNext())
            {
              iter.next().emailed = true;
            }
            stopLoading();

          }

          @Override
          public void onFailure(Throwable caught)
          {
            stopLoading();
          }
        });
      }
    });

    miErrorsAllFixed.setScheduledCommand(new Command()
    {

      @Override
      public void execute()
      {
        startLoading();
        final Set<BasicErrorInfoShared> selected = multipleSelectionModel.getSelectedSet();
        Iterator<BasicErrorInfoShared> iter = selected.iterator();
        ArrayList<String> reportIds = new ArrayList<String>();
        while (iter.hasNext())
        {
          reportIds.add(iter.next().REPORT_ID);
        }
        remoteService.markReportsFixed(reportIds, true, new AsyncCallback<Void>()
        {

          @Override
          public void onSuccess(Void result)
          {
            Iterator<BasicErrorInfoShared> iter = selected.iterator();
            while (iter.hasNext())
            {
              iter.next().fixed = true;
            }
            stopLoading();

          }

          @Override
          public void onFailure(Throwable caught)
          {
            stopLoading();
          }
        });
      }
    });

    miErrorsAllLookedAt.setScheduledCommand(new Command()
    {

      @Override
      public void execute()
      {
        startLoading();
        final Set<BasicErrorInfoShared> selected = multipleSelectionModel.getSelectedSet();
        Iterator<BasicErrorInfoShared> iter = selected.iterator();
        ArrayList<String> reportIds = new ArrayList<String>();
        while (iter.hasNext())
        {
          reportIds.add(iter.next().REPORT_ID);
        }
        remoteService.markReportsLookedAt(reportIds, true, new AsyncCallback<Void>()
        {

          @Override
          public void onSuccess(Void result)
          {
            Iterator<BasicErrorInfoShared> iter = selected.iterator();
            while (iter.hasNext())
            {
              iter.next().lookedAt = true;
            }
            stopLoading();

          }

          @Override
          public void onFailure(Throwable caught)
          {
            stopLoading();
          }
        });

      }
    });

    miReportsEMailSelected.setScheduledCommand(new Command()
    {

      @Override
      public void execute()
      {
        final Set<BasicErrorInfoShared> selected = multipleSelectionModel.getSelectedSet();
        Iterator<BasicErrorInfoShared> iter = selected.iterator();
        ArrayList<String> reportIds = new ArrayList<String>();
        while (iter.hasNext())
        {
          reportIds.add(iter.next().REPORT_ID);
        }

        EMailTemplateSend.doDialog(mLoginInfo, mAppPackageShared, reportIds, remoteService, new EMailTemplateSend.DialogCallback()
        {

          @Override
          public void result(boolean ok)
          {
            if (ok)
            {
              refreshList();
            }

          }
        });

      }
    });

    miReload.setScheduledCommand(new Command()
    {

      @Override
      public void execute()
      {
        refreshList();

      }
    });

    miErrorsDeleteSelected.setScheduledCommand(new Command()
    {

      @Override
      public void execute()
      {
        if (multipleSelectionModel.getSelectedSet().isEmpty())
          return;
        if (!Window.confirm(constants.errorListConfirmDelete()))
          return;

        final Set<BasicErrorInfoShared> selected = multipleSelectionModel.getSelectedSet();
        Iterator<BasicErrorInfoShared> iter = selected.iterator();
        ArrayList<String> ids = new ArrayList<String>();
        startLoading();
        while (iter.hasNext())
        {
          ids.add(iter.next().REPORT_ID);
        }
        remoteService.deleteReports(ids, new AsyncCallback<Void>()
        {

          @Override
          public void onSuccess(Void result)
          {
            refreshList();

          }

          @Override
          public void onFailure(Throwable caught)
          {
            stopLoading();
            Window.alert(caught.toString());
          }
        });

      }
    });

  };

  @Override
  public void onSelectionChange(SelectionChangeEvent event)
  {
    startLoading();

    BasicErrorInfoShared beio = singleSelectionModel.getSelectedObject();
    mCallbackShowReport.showReport(beio);

  }

  public void setAppPackage(String PACKAGE_NAME)
  {
    packageName = PACKAGE_NAME;
    refreshList();
  }

  public void refreshList()
  {
    startLoading();
    dataProvider.startLoading();
    remoteService.getBasicErrorInfo(packageName, mGetBasicErrorCallback);
  }

  AsyncCallback<List<BasicErrorInfoShared>> mGetBasicErrorCallback = new AsyncCallback<List<BasicErrorInfoShared>>()
                                                                   {

                                                                     @Override
                                                                     public void onSuccess(List<BasicErrorInfoShared> result)
                                                                     {
                                                                       dataProvider.stopLoading(result);
                                                                       stopLoading();

                                                                       sortHandler.setList(dataProvider.getList());
                                                                       // dataProvider.addDataDisplay(dataGrid);

                                                                       ColumnSortEvent.fire(dataGrid, dataGrid.getColumnSortList());
                                                                     }

                                                                     @Override
                                                                     public void onFailure(Throwable caught)
                                                                     {
                                                                       stopLoading();
                                                                       dataProvider.stopLoading(null);

                                                                     }
                                                                   };

  private void initTableColumns(final SelectionModel<BasicErrorInfoShared> selectionModel, ListHandler<BasicErrorInfoShared> sortHandler)
  {
   
    Column<BasicErrorInfoShared, String> userCrashDateColumn = new Column<BasicErrorInfoShared, String>(new TextCell())
    {
      @Override
      public String getValue(BasicErrorInfoShared object)
      { // 2012-12-02T18:07:33.000-06:00
        return object.formatCrashDate;
      }
    };
    userCrashDateColumn.setSortable(true);
    sortHandler.setComparator(userCrashDateColumn, new Comparator<BasicErrorInfoShared>()
    {
      @Override
      public int compare(BasicErrorInfoShared o1, BasicErrorInfoShared o2)
      {
        if (o1.crashDate != null && o2.crashDate != null)
        {
          return o1.crashDate.compareTo(o2.crashDate);
        } else
        {
          return o1.USER_CRASH_DATE.compareTo(o2.USER_CRASH_DATE);
        }
      }
    });
    dataGrid.addColumn(userCrashDateColumn, constants.errorListGridCrashDate());
    dataGrid.setColumnWidth(userCrashDateColumn, 150, Unit.PX);

    // AppVersionName
    Column<BasicErrorInfoShared, String> appVersionNameColumn = new Column<BasicErrorInfoShared, String>(new TextCell())
    {
      @Override
      public String getValue(BasicErrorInfoShared object)
      {
        return object.APP_VERSION_NAME;
      }
    };
    appVersionNameColumn.setSortable(true);
    sortHandler.setComparator(appVersionNameColumn, new Comparator<BasicErrorInfoShared>()
    {
      @Override
      public int compare(BasicErrorInfoShared o1, BasicErrorInfoShared o2)
      {
        return o1.APP_VERSION_NAME.compareTo(o2.APP_VERSION_NAME);
      }
    });
    dataGrid.addColumn(appVersionNameColumn, constants.errorListGridVersion());
    dataGrid.setColumnWidth(appVersionNameColumn, 100, Unit.PX);

  }

  @UiHandler("checkErrorsMultiSelect")
  void onCheckErrorsMultiSelectClick(ClickEvent event)
  {
    if (checkErrorsMultiSelect.getValue())
    {
      dataGrid.setSelectionModel(multipleSelectionModel, DefaultSelectionEventManager.<BasicErrorInfoShared> createDefaultManager());
      popupActions.setEnabled(true);

    } else
    {
      popupActions.setEnabled(false);
      dataGrid.setSelectionModel(singleSelectionModel, DefaultSelectionEventManager.<BasicErrorInfoShared> createDefaultManager());
    }
  }

  RowStyles<BasicErrorInfoShared> mGridRowStyles = new RowStyles<BasicErrorInfoShared>()
                                                 {

                                                   @Override
                                                   public String getStyleNames(BasicErrorInfoShared row, int rowIndex)
                                                   {
                                                     if (!row.fixed && !row.lookedAt)
                                                     {
                                                       return "redback";
                                                     }
                                                     if (!row.fixed && row.lookedAt)
                                                     {
                                                       return "blueback";
                                                     }
                                                     return "whiteback";
                                                   }
                                                 };

  class ListProvider extends ListDataProvider<BasicErrorInfoShared>
  {
    public void startLoading()
    {
      super.updateRowCount(0, false);
    }

    public void stopLoading(List<BasicErrorInfoShared> list)
    {
      if (list != null)
      {
        setList(list);
      }
      super.updateRowCount(getList().size(), true);

    }
  }

}
