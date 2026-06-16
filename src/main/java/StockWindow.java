import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import handler.SinaStockHandler;
import handler.StockRefreshHandler;
import handler.TencentStockHandler;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import quartz.HandlerJob;
import quartz.QuartzManager;
import utils.AnalysisPromptUtils;
import utils.LogUtil;
import utils.PopupsUiUtil;
import utils.WindowUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class StockWindow implements ToolWindowFactory {
    public static final String NAME = "Stock";
    private JPanel mPanel;
    private Project currentProject;

    static StockRefreshHandler handler;

    static JBTable table;
    static JLabel refreshTimeLabel;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.currentProject = project;
        // 保存project到LogUtil，供弹窗使用
        LogUtil.setProject(project);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(mPanel, NAME, false);
        ContentManager contentManager = toolWindow.getContentManager();
        contentManager.addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public boolean isDoNotActivateOnStart() {
        return true;
    }

    public JPanel getmPanel() {
        return mPanel;
    }

    /**
     * 初始化表格和监听器
     */
    private void initTable() {
        refreshTimeLabel = new JLabel();
        refreshTimeLabel.setToolTipText("最后刷新时间");
        refreshTimeLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
        table = new JBTable();
        //记录列名的变化
        table.getTableHeader().addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                StringBuilder tableHeadChange = new StringBuilder();
                for (int i = 0; i < table.getColumnCount(); i++) {
                    tableHeadChange.append(table.getColumnName(i)).append(",");
                }
                PropertiesComponent instance = PropertiesComponent.getInstance();
                //将列名的修改放入环境中 key:stock_table_header_key
                instance.setValue(WindowUtils.STOCK_TABLE_HEADER_KEY, tableHeadChange
                        .substring(0, tableHeadChange.length() > 0 ? tableHeadChange.length() - 1 : 0));

                //LogUtil.info(instance.getValue(WindowUtils.STOCK_TABLE_HEADER_KEY));
            }

        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (table.getSelectedRow() < 0 || handler == null)
                    return;
                int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
                String code = String.valueOf(table.getModel().getValueAt(modelRow, handler.codeColumnIndex));//FIX 移动列导致的BUG
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() > 1) {
                    // 鼠标左键双击
                    try {
                        PopupsUiUtil.showImageByStockCode(code, PopupsUiUtil.StockShowType.min, new Point(e.getXOnScreen(), e.getYOnScreen()));
                    } catch (MalformedURLException ex) {
                        ex.printStackTrace();
                        LogUtil.info(ex.getMessage());
                    }
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    //鼠标右键
                    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PopupsUiUtil.StockShowType>("",
                            PopupsUiUtil.StockShowType.values()) {
                        @Override
                        public @NotNull String getTextFor(PopupsUiUtil.StockShowType value) {
                            return value.getDesc();
                        }

                        @Override
                        public @Nullable PopupStep onChosen(PopupsUiUtil.StockShowType selectedValue, boolean finalChoice) {
                            try {
                                PopupsUiUtil.showImageByStockCode(code, selectedValue, new Point(e.getXOnScreen(), e.getYOnScreen()));
                            } catch (MalformedURLException ex) {
                                ex.printStackTrace();
                                LogUtil.info(ex.getMessage());
                            }
                            return super.onChosen(selectedValue, finalChoice);
                        }
                    }).show(RelativePoint.fromScreen(new Point(e.getXOnScreen(), e.getYOnScreen())));
                }
            }
        });
    }

    public StockWindow() {
        // 初始化表格
        initTable();

        //切换接口
        handler = factoryHandler();

        AnActionButton refreshAction = new AnActionButton("停止刷新当前表格数据", AllIcons.Actions.Pause) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                stop();
                this.setEnabled(false);
            }
        };

        // 创建AI分析下拉菜单按钮
        AnActionButton analysisAction = new AnActionButton("AI分析提示词", AllIcons.Actions.IntentionBulb) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // 创建下拉菜单选项
                String[] options = {
                    "今日机会分析",
                    "明日机会分析",
                    "仓位风险分析",
                    "昨日复盘分析",
                    "今日复盘分析",
                    "异动实事分析"
                };
                
                String[] types = {
                    "today_opportunity",
                    "tomorrow_opportunity",
                    "position_risk",
                    "yesterday_review",
                    "today_review",
                    "abnormal_movement"
                };
                
                // 显示弹出菜单
                JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>("选择分析类型", options) {
                    @Override
                    public @NotNull String getTextFor(String value) {
                        return value;
                    }

                    @Override
                    public @Nullable PopupStep onChosen(String selectedValue, boolean finalChoice) {
                        // 找到对应的type
                        int index = -1;
                        for (int i = 0; i < options.length; i++) {
                            if (options[i].equals(selectedValue)) {
                                index = i;
                                break;
                            }
                        }
                        
                        if (index >= 0 && index < types.length) {
                            copyAnalysisToClipboard(types[index]);
                        }
                        
                        return FINAL_CHOICE;
                    }
                }).showInBestPositionFor(e.getDataContext());
            }
        };
        
        // 创建重置按钮
        AnActionButton resetAction = new AnActionButton("重置表格", AllIcons.Actions.Restart) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // 重新初始化表格
                apply();
            }
        };
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(table)
                .addExtraActions(
                    new AnActionButton("持续刷新当前表格数据", AllIcons.Actions.Refresh) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                            refresh();
                            refreshAction.setEnabled(true);
                        }
                    },
                    refreshAction,
                    analysisAction,
                    resetAction
                )
                .setToolbarPosition(ActionToolbarPosition.TOP);
        JPanel toolPanel = toolbarDecorator.createPanel();
        toolbarDecorator.getActionsPanel().add(refreshTimeLabel, BorderLayout.EAST);
        toolPanel.setBorder(new EmptyBorder(0,0,0,0));
        mPanel.add(toolPanel, BorderLayout.CENTER);
        // 非主要tab，需要创建，创建时立即应用数据
        apply();
    }

    private static StockRefreshHandler factoryHandler(){
        boolean useSinaApi = PropertiesComponent.getInstance().getBoolean("key_stocks_sina");
        if (useSinaApi){
            if (handler instanceof SinaStockHandler){
                return handler;
            }
            return new SinaStockHandler(table, refreshTimeLabel);
        }
        if (handler instanceof TencentStockHandler){
            return handler;
        }
        return  new TencentStockHandler(table, refreshTimeLabel);
    }

    public static void apply() {
        if (handler != null) {
            handler = factoryHandler();
            PropertiesComponent instance = PropertiesComponent.getInstance();
            handler.setStriped(instance.getBoolean("key_table_striped"));
            handler.clearRow();
            handler.setupTable(loadStocks());
            refresh();
            // 清除表头排序状态，移除排序箭头
            if (table.getRowSorter() != null) {
                table.getRowSorter().setSortKeys(null);
            }
        }
    }
    public static void refresh() {
        if (handler != null) {
            PropertiesComponent instance = PropertiesComponent.getInstance();
            handler.refreshColorful(instance.getBoolean("key_colorful"));
            List<String> codes = loadStocks();
            if (CollectionUtils.isEmpty(codes)) {
                stop(); //如果没有数据则不需要启动时钟任务浪费资源
            } else {
                handler.handle(codes);
                QuartzManager quartzManager = QuartzManager.getInstance(NAME);
                HashMap<String, Object> dataMap = new HashMap<>();
                dataMap.put(HandlerJob.KEY_HANDLER, handler);
                dataMap.put(HandlerJob.KEY_CODES, codes);
                String cronExpression = instance.getValue("key_cron_expression_stock");
                if (StringUtils.isEmpty(cronExpression)) {
                    cronExpression = "*/10 * * * * ?";
                }
                quartzManager.runJob(HandlerJob.class, cronExpression, dataMap);
            }
        }
    }

    public static void stop() {
        QuartzManager.getInstance(NAME).stopJob();
        if (handler != null) {
            handler.stopHandle();
        }
    }

    private static List<String> loadStocks(){
        return SettingsWindow.getConfigList("key_stocks");
    }

    /**
     * 生成股票分析文本并复制到剪贴板
     * @param type 分析类型
     */
    private void copyAnalysisToClipboard(String type) {
        if (handler == null || table.getModel().getRowCount() == 0) {
            JOptionPane.showMessageDialog(mPanel, "当前没有股票数据", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder analysisText = new StringBuilder();

        // 获取基础模板
        String baseTemplate = AnalysisPromptUtils.getBaseStockInfoTemplate();

        // 生成时间字符串
        String timeStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 生成股票列表字符串
        StringBuilder stockListBuilder = new StringBuilder();
        int rowCount = table.getModel().getRowCount();
        for (int i = 0; i < rowCount; i++) {
            int modelRow = table.convertRowIndexToModel(i);
            String code = String.valueOf(table.getModel().getValueAt(modelRow, handler.codeColumnIndex));
            String name = String.valueOf(table.getModel().getValueAt(modelRow, 1)); // 股票名称
            String change = String.valueOf(table.getModel().getValueAt(modelRow, 2)); // 涨跌
            String changePercent = String.valueOf(table.getModel().getValueAt(modelRow, 3)); // 涨跌幅
            String currentPrice = String.valueOf(table.getModel().getValueAt(modelRow, 4)); // 当前价
            String progress = String.valueOf(table.getModel().getValueAt(modelRow, 5)); // 进度
            String expectedPrice = String.valueOf(table.getModel().getValueAt(modelRow, 6)); // 预期价
            String incomePercent = String.valueOf(table.getModel().getValueAt(modelRow, 7)); // 收益率
            String income = String.valueOf(table.getModel().getValueAt(modelRow, 8)); // 收益
            String bonds = String.valueOf(table.getModel().getValueAt(modelRow, 9)); // 持仓
            String costPrice = String.valueOf(table.getModel().getValueAt(modelRow, 10)); // 成本价
            String highPrice = String.valueOf(table.getModel().getValueAt(modelRow, 11)); // 最高价
            String lowPrice = String.valueOf(table.getModel().getValueAt(modelRow, 12)); // 最低价
            String conditionPrice = String.valueOf(table.getModel().getValueAt(modelRow, 13)); // 条件价
            String conditionRatio = String.valueOf(table.getModel().getValueAt(modelRow, 14)); // 条件比

            stockListBuilder.append(String.format("%d. %s(%s)\n", i + 1, name, code));
            stockListBuilder.append(String.format("   当前价: %s, 涨跌: %s, 涨跌幅: %s\n", currentPrice, change, changePercent));
            stockListBuilder.append(String.format("   最高价: %s, 最低价: %s\n", highPrice, lowPrice));
            if (!"--".equals(costPrice) && !"null".equals(costPrice)) {
                stockListBuilder.append(String.format("   成本价: %s, 持仓: %s股, 收益率: %s%%, 收益: %s元\n",
                    costPrice, bonds, incomePercent, income));
            }
            if (!"--".equals(expectedPrice) && !"null".equals(expectedPrice)) {
                stockListBuilder.append(String.format("   预期价: %s, 进度: %s\n", expectedPrice, progress));
            }
            if (!"--".equals(conditionPrice) && !"null".equals(conditionPrice)) {
                stockListBuilder.append(String.format("   条件价: %s, 条件比: %s\n", conditionPrice, conditionRatio));
            }
            stockListBuilder.append("\n");
        }

        // 填充基础模板
        String filledBaseTemplate = AnalysisPromptUtils.fillTemplate(baseTemplate, timeStr, stockListBuilder.toString());
        analysisText.append(filledBaseTemplate);

        // 根据类型添加对应的分析要求
        String analysisPrompt;
        switch (type) {
            case "today_opportunity":
                analysisPrompt = AnalysisPromptUtils.getTodayOpportunityPrompt();
                break;
            case "tomorrow_opportunity":
                analysisPrompt = AnalysisPromptUtils.getTomorrowOpportunityPrompt();
                break;
            case "position_risk":
                analysisPrompt = AnalysisPromptUtils.getPositionRiskPrompt();
                break;
            case "yesterday_review":
                analysisPrompt = AnalysisPromptUtils.getYesterdayReviewPrompt();
                break;
            case "today_review":
                analysisPrompt = AnalysisPromptUtils.getTodayReviewPrompt();
                break;
            case "abnormal_movement":
                analysisPrompt = AnalysisPromptUtils.getAbnormalMovementPrompt();
                break;
            default:
                analysisPrompt = "请给出综合分析建议。";
        }

        analysisText.append(analysisPrompt);

        // 复制到剪贴板
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(analysisText.toString());
            clipboard.setContents(selection, null);

            // 获取分析类型名称
            String typeName;
            switch (type) {
                case "today_opportunity":
                    typeName = "今日机会";
                    break;
                case "tomorrow_opportunity":
                    typeName = "明日机会";
                    break;
                case "position_risk":
                    typeName = "仓位风险";
                    break;
                case "yesterday_review":
                    typeName = "昨日复盘";
                    break;
                case "today_review":
                    typeName = "今日复盘";
                    break;
                case "abnormal_movement":
                    typeName = "异动实事";
                    break;
                default:
                    typeName = "股票";
            }

            // 使用状态栏提示代替弹窗
            LogUtil.info("✓ [" + typeName + "]分析文本已复制到剪贴板，可直接粘贴到AI助手进行分析");
            
            // 在刷新时间标签处显示临时提示
            String originalText = refreshTimeLabel.getText();
            refreshTimeLabel.setText("✓ " + typeName + "复制成功！");
            refreshTimeLabel.setForeground(new java.awt.Color(40, 167, 69)); // 绿色
            
            // 2秒后恢复原状
            Timer timer = new Timer(2000, e -> {
                refreshTimeLabel.setText(originalText);
                refreshTimeLabel.setForeground(UIManager.getColor("Label.foreground"));
            });
            timer.setRepeats(false);
            timer.start();
            
        } catch (Exception ex) {
            ex.printStackTrace();
            LogUtil.info("✗ 复制到剪贴板失败：" + ex.getMessage());
            
            // 显示错误提示
            String originalText = refreshTimeLabel.getText();
            refreshTimeLabel.setText("✗ 复制失败");
            refreshTimeLabel.setForeground(new java.awt.Color(220, 53, 69)); // 红色
            
            Timer timer = new Timer(2000, e -> {
                refreshTimeLabel.setText(originalText);
                refreshTimeLabel.setForeground(UIManager.getColor("Label.foreground"));
            });
            timer.setRepeats(false);
            timer.start();
        }
    }

}
