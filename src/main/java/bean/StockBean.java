package bean;

import org.apache.commons.lang3.StringUtils;
import utils.PinYinUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

public class StockBean {
    private String code;
    private String name;
    private String now;
    private String change;//涨跌
    private String changePercent;
    private String time;
    /**
     * 最高价
     */
    private String max;
    /**
     * 最低价
     */
    private String min;

    private String costPrise;//成本价
//    private String cost;//成本
    private String bonds;//持仓
    private String incomePercent;//收益率
    private String income;//收益
    private String targetPrice;//目标价

    public StockBean() {
    }

    //配置code同时配置成本价和成本值
    public StockBean(String code) {
        if (StringUtils.isNotBlank(code)) {
            String[] codeStr = code.split(",");
            if (codeStr.length > 3) {
                this.code = codeStr[0];
                this.targetPrice = codeStr[1];
                this.costPrise = codeStr[2];
//                this.cost = codeStr[2];
                this.bonds = codeStr[3];
            } else if (codeStr.length > 2) {
                this.code = codeStr[0];
                this.targetPrice = "--";
                this.costPrise = codeStr[1];
//                this.cost = codeStr[2];
                this.bonds = codeStr[2];
            } else if (codeStr.length > 1) {
                this.code = codeStr[0];
                this.targetPrice = codeStr[1];
                this.costPrise = "--";
//                this.cost = "--";
                this.bonds = "--";
            } else {
                this.code = codeStr[0];
                this.costPrise = "--";
//                this.cost = "--";
                this.bonds = "--";
                this.targetPrice = "--";
            }
        } else {
            this.code = code;
            this.costPrise = "--";
            this.bonds = "--";
            this.targetPrice = "--";
        }
        this.name = "--";
    }

    public StockBean(String code, Map<String, String[]> codeMap){
        this.code = code;
        if(codeMap.containsKey(code)){
            String[] codeStr = codeMap.get(code);
            if (codeStr.length > 3) {
                this.code = codeStr[0];
                this.targetPrice = codeStr[1];
                this.costPrise = codeStr[2];
//                this.cost = codeStr[2];
                this.bonds = codeStr[3];
            } else if (codeStr.length > 2) {
                this.code = codeStr[0];
                this.targetPrice = "--";
                this.costPrise = codeStr[1];
//                this.cost = codeStr[2];
                this.bonds = codeStr[2];
            } else if (codeStr.length > 1) {
                this.code = codeStr[0];
                this.targetPrice = codeStr[1];
                this.costPrise = "--";
//                this.cost = "--";
                this.bonds = "--";
            }
        }
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNow() {
        return now;
    }

    public void setNow(String now) {
        this.now = now;
    }

    public String getChange() {
        return change;
    }

    public void setChange(String change) {
        this.change = change;
    }

    public String getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(String changePercent) {
        this.changePercent = changePercent;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getMax() {
        return max;
    }

    public void setMax(String max) {
        this.max = max;
    }

    public String getMin() {
        return min;
    }

    public void setMin(String min) {
        this.min = min;
    }

    public String getCostPrise() {
        return costPrise;
    }

    public void setCostPrise(String costPrise) {
        this.costPrise = costPrise;
    }

    public String getBonds() {
        return bonds;
    }

    public void setBonds(String bonds) {
        this.bonds = bonds;
    }

    //    public String getCost() {
//        return cost;
//    }
//
//    public void setCost(String cost) {
//        this.cost = cost;
//    }

    public String getIncomePercent() {
        return incomePercent;
    }

    public void setIncomePercent(String incomePercent) {
        this.incomePercent = incomePercent;
    }

    public String getIncome() {
        return income;
    }

    public void setIncome(String income) {
        this.income = income;
    }

    public String getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(String targetPrice) {
        this.targetPrice = targetPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockBean bean = (StockBean) o;
        return Objects.equals(code, bean.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }


    /**
     * 返回列名的VALUE 用作展示
     *
     * @param colums   字段名
     * @param colorful 隐蔽模式
     * @return 对应列名的VALUE值 无法匹配返回""
     */
    public String getValueByColumn(String colums, boolean colorful) {
        switch (colums) {
            case "编码":
                return this.getCode();
            case "股票名称":
                return colorful ? this.getName() : PinYinUtils.toPinYin(this.getName());
            case "当前价":
                return this.getNow();
            case "涨跌":
                String changeStr = "--";
                if (this.getChange() != null) {
                    changeStr = this.getChange().startsWith("-") ? this.getChange() : "+" + this.getChange();
                }
                return changeStr;
            case "涨跌幅":
                String changePercentStr = "--";
                if (this.getChangePercent() != null) {
                    changePercentStr = this.getChangePercent().startsWith("-") ? this.getChangePercent() : "+" + this.getChangePercent();
                }
                return changePercentStr + "%";
            case "最高价":
                return this.getMax();
            case "最低价":
                return this.getMin();
            case "成本价":
                return this.getCostPrise();
            case "持仓":
                return this.getBonds();
            case "收益率":
                // 只有当成本价不是"--"且有收益率数据时才显示
                if (this.getCostPrise() != null && !"--".equals(this.getCostPrise()) && this.getIncomePercent() != null) {
                    return this.getIncomePercent() + "%";
                }
                return this.getIncomePercent() != null ? this.getIncomePercent() : "--";
            case "收益":
                return this.getIncome();
            case "目标价":
                return this.getTargetPrice();
            case "进度":
                return calculateProgress();
            case "更新时间":
                String timeStr = "--";
                if (this.getTime() != null) {
                    timeStr = this.getTime().substring(8);
                }
                return timeStr;
            default:
                return "";

        }
    }

    /**
     * 计算当前价到目标价的差价
     * @return 差价字符串（目标价-当前价）
     */
    private String calculateProgress() {
        if ("--".equals(this.now) || "--".equals(this.targetPrice) || 
            this.now == null || this.targetPrice == null) {
            return "--";
        }
        try {
            double currentPrice = Double.parseDouble(this.now);
            double target = Double.parseDouble(this.targetPrice);
            double diff = target - currentPrice;
            // 保留两位小数，正数表示还有上涨空间，负数表示已超过目标价
            return String.format("%.2f", diff);
        } catch (NumberFormatException e) {
            return "--";
        }
    }
}
