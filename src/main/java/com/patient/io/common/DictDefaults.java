package com.patient.io.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台内置字典定义。
 * <p>用于「一键初始化内置字典」：库里缺哪个字典/字典项就补哪个，已存在的（含医院自行改过的）一律不覆盖。
 * 前端 utils/dict.ts 里的 DICT_DEFAULTS 与此保持一致，作为接口不可用时的兜底。</p>
 */
public final class DictDefaults {

    private DictDefaults() {
    }

    public static class Item {
        public final String value;
        public final String label;

        public Item(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public static class TypeDef {
        public final String code;
        public final String name;
        public final String remark;
        public final List<Item> items;

        public TypeDef(String code, String name, String remark, List<Item> items) {
            this.code = code;
            this.name = name;
            this.remark = remark;
            this.items = items;
        }
    }

    private static Item i(String value, String label) {
        return new Item(value, label);
    }

    /** 按业务归属分组，便于维护页展示顺序 */
    public static final List<TypeDef> ALL = new ArrayList<>();

    static {
        ALL.add(new TypeDef("patient_type", "人群类别", "住院病患人群划分，影响出入量参考值",
                Arrays.asList(i("ADULT", "成人"), i("INFANT", "婴儿"))));

        ALL.add(new TypeDef("admission_type", "入院方式", "平诊/急诊，急诊患者在看板高亮",
                Arrays.asList(i("ROUTINE", "平诊"), i("EMERGENCY", "急诊"))));

        ALL.add(new TypeDef("nursing_level", "护理级别", "护理分级，特级/一级计入重症护理",
                Arrays.asList(i("SPECIAL", "特级护理"), i("LEVEL_1", "一级护理"),
                        i("LEVEL_2", "二级护理"), i("LEVEL_3", "三级护理"))));

        ALL.add(new TypeDef("drainage_tube", "留置管路", "病患留置管路类型，可多选",
                Arrays.asList(i("CATHER", "留置导尿管"), i("GASTRIC", "胃肠减压管"),
                        i("DRAINAGE", "引流管(胸/腹腔)"), i("PICC", "PICC置管"),
                        i("CVC", "中心静脉导管"), i("PORT", "输液港"),
                        i("NASOBILIARY", "鼻胆管"), i("ABDOM_DRAIN", "腹腔引流管"),
                        i("OTHER", "其他管道"))));

        ALL.add(new TypeDef("nurse_role", "医护岗位角色", "护士档案岗位",
                Arrays.asList(i("HEAD_NURSE", "护士长"), i("NURSE", "责任护士"))));

        ALL.add(new TypeDef("infusion_usage", "补液给药用法", "补液登记的给药途径，可自定义扩展",
                Arrays.asList(i("ivg", "静滴"), i("ibn", "静推"), i("ivdrip", "静脉滴注"),
                        i("iv", "静脉注射"), i("im", "肌注"), i("sc", "皮下注射"), i("po", "口服"))));

        ALL.add(new TypeDef("blood_sugar_type", "血糖测量时点", "交接班血糖记录时点",
                Arrays.asList(i("空腹", "空腹"), i("餐后2h", "餐后2h"), i("随机", "随机"),
                        i("睡前", "睡前"), i("凌晨", "凌晨3点"))));

        ALL.add(new TypeDef("laxative_drug", "常用泻药", "交接班泻药记录的常用药名",
                Arrays.asList(i("复方聚乙二醇电解质散", "复方聚乙二醇电解质散"),
                        i("聚乙二醇4000散", "聚乙二醇4000散"),
                        i("乳果糖口服溶液", "乳果糖口服溶液"),
                        i("硫酸镁", "硫酸镁"),
                        i("番泻叶", "番泻叶"),
                        i("开塞露", "开塞露"),
                        i("甘油灌肠剂", "甘油灌肠剂"))));

        ALL.add(new TypeDef("drug_dosage_form", "药品剂型", "补液药品字典的剂型",
                Arrays.asList(i("注射剂", "注射剂"), i("粉针剂", "粉针剂"), i("片剂", "片剂"),
                        i("胶囊剂", "胶囊剂"), i("颗粒剂", "颗粒剂"), i("口服溶液", "口服溶液"),
                        i("乳膏剂", "乳膏剂"), i("滴眼液", "滴眼液"), i("栓剂", "栓剂"))));

        ALL.add(new TypeDef("drug_unit", "药品单位", "补液药品字典的计量单位",
                Arrays.asList(i("ml", "ml"), i("g", "g"), i("mg", "mg"), i("支", "支"),
                        i("瓶", "瓶"), i("袋", "袋"), i("片", "片"), i("粒", "粒"), i("IU", "IU"))));

        ALL.add(new TypeDef("drug_route", "给药途径", "补液药品字典默认给药途径",
                Arrays.asList(i("ivg", "静滴"), i("ibn", "静推"), i("ivdrip", "静脉滴注"),
                        i("iv", "静脉注射"), i("im", "肌注"), i("sc", "皮下注射"),
                        i("po", "口服"), i("外用", "外用"))));

        ALL.add(new TypeDef("diagnosis", "常用临床诊断", "病患建档时的诊断联想词，可直接输入其它诊断",
                Arrays.asList(i("胃癌术后", "胃癌术后"),
                        i("胆囊结石伴急性胆囊炎", "胆囊结石伴急性胆囊炎"),
                        i("急性心肌梗死合并心力衰竭", "急性心肌梗死合并心力衰竭"),
                        i("高血压脑出血恢复期", "高血压脑出血恢复期"),
                        i("2型糖尿病", "2型糖尿病"),
                        i("慢性阻塞性肺疾病急性加重", "慢性阻塞性肺疾病急性加重"),
                        i("急性胰腺炎", "急性胰腺炎"),
                        i("肠梗阻", "肠梗阻"),
                        i("肝硬化失代偿期", "肝硬化失代偿期"),
                        i("泌尿系结石", "泌尿系结石"),
                        i("肺部感染", "肺部感染"),
                        i("脑梗死", "脑梗死"))));
    }

    /** code -> TypeDef，方便按编码取元信息 */
    public static Map<String, TypeDef> map() {
        Map<String, TypeDef> map = new LinkedHashMap<>();
        for (TypeDef t : ALL) {
            map.put(t.code, t);
        }
        return map;
    }
}
