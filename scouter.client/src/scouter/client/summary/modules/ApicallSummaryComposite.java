package scouter.client.summary.modules;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import scouter.client.Images;
import scouter.client.model.TextProxy;
import scouter.client.net.TcpProxy;
import scouter.client.popup.CalendarDialog;
import scouter.client.popup.CalendarDialog.ILoadCounterDialog;
import scouter.client.sorter.ColumnLabelSorter;
import scouter.client.util.ExUtil;
import scouter.client.util.TimeUtil;
import scouter.lang.pack.MapPack;
import scouter.lang.pack.Pack;
import scouter.lang.value.ListValue;
import scouter.net.RequestCmd;
import scouter.util.DateUtil;
import scouter.util.FormatUtil;

public class ApicallSummaryComposite extends AbstractSummaryComposite {
	
	public ApicallSummaryComposite(Composite parent, int style) {
		super(parent, style);
	}
	
	protected void createColumns() {
		for (ApicallColumnEnum column : ApicallColumnEnum.values()) {
			TableViewerColumn c = createTableViewerColumn(column.getTitle(), column.getWidth(), column.getAlignment(), column.isResizable(), column.isMoveable(), column.isNumber());
			ColumnLabelProvider labelProvider = null;
			switch (column) {
			case URL:
				labelProvider = new ColumnLabelProvider() {
					@Override
					public String getText(Object element) {
						if (element instanceof SummaryData) {
							return TextProxy.apicall.getText(((SummaryData) element).hash);
						}
						return null;
					}
				};
				break;
			case COUNT:
				labelProvider = new ColumnLabelProvider() {
					@Override
					public String getText(Object element) {
						if (element instanceof SummaryData) {
							return FormatUtil.print(((SummaryData) element).count, "#,##0");
						}
						return null;
					}
				};
				break;
			case ERROR_COUNT:
				labelProvider = new ColumnLabelProvider() {
					@Override
					public String getText(Object element) {
						if (element instanceof SummaryData) {
							return FormatUtil.print(((SummaryData) element).errorCount, "#,##0");
						}
						return null;
					}
				};
				break;
			case ELAPSED_SUM:
				labelProvider = new ColumnLabelProvider() {
					@Override
					public String getText(Object element) {
						if (element instanceof SummaryData) {
							return FormatUtil.print(((SummaryData) element).elapsedSum, "#,##0");
						}
						return null;
					}
				};
				break;
			case ELAPSED_AVG:
				labelProvider = new ColumnLabelProvider() {
					@Override
					public String getText(Object element) {
						if (element instanceof SummaryData) {
							SummaryData data = (SummaryData) element;
							return FormatUtil.print(data.elapsedSum / (double) data.count , "#,##0");
						}
						return null;
					}
				};
				break;
			}
			if (labelProvider != null) {
				c.setLabelProvider(labelProvider);
			}
		}
	}
	
	enum ApicallColumnEnum {

	    URL("URL", 150, SWT.CENTER, true, true, false),
	    COUNT("Count", 70, SWT.RIGHT, true, true, true),
	    ERROR_COUNT("Error", 70, SWT.RIGHT, true, true, true),
	    ELAPSED_SUM("Total Elapsed(ms)", 150, SWT.RIGHT, true, true, true),
	    ELAPSED_AVG("Avg Elapsed(ms)", 150, SWT.RIGHT, true, true, true);

	    private final String title;
	    private final int width;
	    private final int alignment;
	    private final boolean resizable;
	    private final boolean moveable;
	    private final boolean isNumber;

	    private ApicallColumnEnum(String text, int width, int alignment, boolean resizable, boolean moveable, boolean isNumber) {
	        this.title = text;
	        this.width = width;
	        this.alignment = alignment;
	        this.resizable = resizable;
	        this.moveable = moveable;
	        this.isNumber = isNumber;
	    }
	    
	    public String getTitle(){
	        return title;
	    }

	    public int getAlignment(){
	        return alignment;
	    }

	    public boolean isResizable(){
	        return resizable;
	    }

	    public boolean isMoveable(){
	        return moveable;
	    }

		public int getWidth() {
			return width;
		}
		
		public boolean isNumber() {
			return this.isNumber;
		}
	}
	
	class LoadApicallSummaryJob extends Job {
		
		MapPack param;

		public LoadApicallSummaryJob(MapPack param) {
			super("Loading...");
			this.param = param;
		}

		protected IStatus run(IProgressMonitor monitor) {
			TcpProxy tcp = TcpProxy.getTcpProxy(serverId);
			Pack p = null;
			try {
				p = tcp.getSingle(RequestCmd.LOAD_APICALL_SUMMARY, param);
			} catch (Exception e) {
				e.printStackTrace();
				return Status.CANCEL_STATUS;
			} finally {
				TcpProxy.putTcpProxy(tcp);
			}
			
			if (p != null) {
				final List<SummaryData> list = new ArrayList<SummaryData>();
				MapPack m = (MapPack) p;
				ListValue idLv = m.getList("id");
				ListValue countLv = m.getList("count");
				ListValue errorLv = m.getList("error");
				ListValue elapsedLv = m.getList("elapsed");
				for (int i = 0; i < idLv.size(); i++) {
					SummaryData data = new SummaryData();
					data.hash = idLv.getInt(i);
					data.count = countLv.getInt(i);
					data.errorCount = errorLv.getInt(i);
					data.elapsedSum = elapsedLv.getLong(i);
					list.add(data);
				}
				TextProxy.apicall.load(date, idLv, serverId);
				ExUtil.exec(viewer.getTable(), new Runnable() {
					public void run() {
						viewer.setInput(list);
					}
				});
			}
			 
			return Status.OK_STATUS;
		}
	}

	protected void getSummaryData() {
		new LoadApicallSummaryJob(param).schedule();
	}

	protected String getTitle() {
		return "APICALL";
	}
}
