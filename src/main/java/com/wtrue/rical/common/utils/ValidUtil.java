package com.wtrue.rical.common.utils;

import com.alibaba.fastjson.JSON;
import com.wtrue.rical.common.domain.BaseError;
import com.wtrue.rical.common.domain.ValidObj;
import com.wtrue.rical.common.enums.ErrorEnum;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @description:
 * @author: meidanlong
 * @date: 2021/3/21 8:24 PM
 */
@Slf4j
public class ValidUtil {

    private boolean valid = true;
    private BaseError error = new BaseError();

    private ValidObj curValidObj;
    private Stack<ValidObj> objStack = new Stack<>();

    /**
     * 有参构造函数，传入对象用于初始化
     * @param objName
     * @param getObj 传入对象或者获取对象过程
     */
    public ValidUtil(String objName, Supplier getObj){
        try {
            if(StringUtil.isEmpty(objName)){
                populateError("baseObjName is empty");
            }
            Object curObj = getObj.get();
            if(curObj == null){
                populateError("'"+ objName +"' is null");
            }
            ValidObj validObj = new ValidObj(objName, curObj);
            this.objStack.push(validObj);
            this.curValidObj = validObj;
        }catch (NullPointerException npe){
            populateError("NPE in the process of getting '"+ objName +"'");
        }
    }

    /**
     * 获取子对象
     * @param fieldName
     * @return
     */
    public ValidUtil sub(String fieldName){
        // assert
        if(!this.valid || !notNull(fieldName).isValid()){
            return this;
        }
        // main
        ValidObj subValidObj = new ValidObj(fieldName, getMapValue(fieldName));
        this.objStack.push(subValidObj);
        this.curValidObj = subValidObj;
        return this;
    }

    /**
     * 获取父对象
     * @return
     */
    public ValidUtil sup(){
        // assert
        if(!this.valid){
            return this;
        }
        // main
        if(objStack == null || objStack.size() <= 1){
            log.error("objStack is null or objStack size less then 1, objStack={}", objStack);
        }else{
            objStack.pop();
            ValidObj supValidObj = objStack.peek();
            this.curValidObj = supValidObj;
        }
        return this;
    }

    /**
     * 批量判空
     * @param fieldNames
     * @return
     */
    public ValidUtil notNull(String... fieldNames){
        for (String fieldName : fieldNames) {
            notNull(fieldName);
        }
        return this;
    }

    /**
     * 对象属性非空校验
     *
     * 1、属性对象非空校验
     * 2、String 校验null和""
     * 3、List 校验null和size==0
     *
     * @param fieldName
     * @return
     */
    public ValidUtil notNull(String fieldName){

        // assert
        if(!this.valid){
            return this;
        }
        Object curObj = curValidObj.getCurObj();
        String curObjName = curValidObj.getCurObjName();
        if(curObj == null || StringUtil.isEmpty(curObjName)){
            populateError("curObj is null or curObjName is empty");
            return this;
        }
        // main
        try {
            Field field = curObj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object fieldValue = field.get(curObj);
            if(fieldValue == null){
                populateError("'"+ curValidObj.getCurObjName()+"#"+fieldName+"' should not be null");
            }else if(fieldValue instanceof String && StringUtil.isEmpty((String) fieldValue)){
                populateError("'"+ curValidObj.getCurObjName()+"#"+fieldName+"' should not be empty");
            }else if(fieldValue instanceof List && ((List<?>) fieldValue).size() <= 0){
                populateError("'"+ curValidObj.getCurObjName()+"#"+fieldName+"' should not be empty");
            }else{
                setMapValue(fieldName, fieldValue);
            }
        } catch (NoSuchFieldException e) {
            populateError("there is not a filed named '"+ curValidObj.getCurObjName()+"#"+fieldName+"'");
        } catch (IllegalAccessException e) {
            populateError("'"+ curValidObj.getCurObjName()+"#"+fieldName+"' access illegal");
        }
        return this;
    }

    /**
     * 对象属性字符长度校验
     * @param fieldName
     * @param max
     * @return
     */
    public ValidUtil stringMaxLength(String fieldName, long max){
        // assert
        if(!this.valid || !notNull(fieldName).isValid()){
            return this;
        }
        // main
        Object value = getMapValue(fieldName);
        if(value instanceof String){
            String toValidate = value.toString();
            if(toValidate.length() > max){
                populateError("length of '"+ curValidObj.getCurObjName()+"#"+fieldName+"' should less then '"+max+"'");
            }
        }else{
            populateError("'"+ curValidObj.getCurObjName()+"#"+fieldName+"' is not a string");
        }

        return this;
    }

    /**
     * 对象属性最大值校验
     * @param fieldName
     * @param max
     * @return
     */
    public ValidUtil maxLong(String fieldName, long max){
        // assert
        if(!this.valid || !notNull(fieldName).isValid()){
            return this;
        }
        // main
        try {
            Long toValidate = Long.valueOf(getMapValue(fieldName).toString());
            if(toValidate > max){
                populateError("value of '"+ curValidObj.getCurObjName()+"#"+fieldName+"' should less then '"+max+"'");
            }
        }catch (NumberFormatException e){
            populateError("'"+ curValidObj.getCurObjName()+"#"+fieldName+"' can not cast to Long");
        }
        return this;
    }

    /**
     * 对象属性最小值校验
     * @param fieldName
     * @param min
     * @return
     */
    public ValidUtil minLong(String fieldName, long min){
        // assert
        if(!this.valid || !notNull(fieldName).isValid()){
            return this;
        }
        // main
        try {
            Long toValidate = Long.valueOf(getMapValue(fieldName).toString());
            if(toValidate < min){
                populateError("value of '"+ curValidObj.getCurObjName()+"#"+fieldName+"' should more then '"+min+"'");
            }
        }catch (NumberFormatException e){
            populateError("'"+ curValidObj.getCurObjName()+"#"+fieldName+"' can not cast to Long");
        }
        return this;
    }

    /**
     * 对象属性在两值之间校验
     * @param fieldName
     * @param min
     * @param max
     * @return
     */
    public ValidUtil betweenLong(String fieldName, long min, long max){
        // assert
        if(!this.valid){
            return this;
        }
        // main
        minLong(fieldName, min);
        maxLong(fieldName, max);
        return this;
    }

    /**
     * 集合最大数量
     * @param fieldName
     * @param max
     * @return
     */
    public ValidUtil listMaxSize(String fieldName, long max){
        // assert
        if(!this.valid || !notNull(fieldName).isValid()){
            return this;
        }
        // main
        try{
            List list = (List) getMapValue(fieldName);
            if(list.size()>max){
                populateError("size of '"+ curValidObj.getCurObjName()+"#"+fieldName+"' should less then '"+max+"'");
            }
        }catch (Exception e){
            populateError("'"+ curValidObj.getCurObjName()+"#"+fieldName+"' can not cast to List");
        }
        return this;
    }

    /**
     * offset和Limit校验，并赋默认值
     * @param defaultOffset
     * @param defaultLimit
     * @return
     */
    public ValidUtil offsetAndLimit(int defaultOffset, int defaultLimit){
        // assert
        if(!this.valid){
            return this;
        }
        Object curObj = curValidObj.getCurObj();
        String curObjName = curValidObj.getCurObjName();
        if(curObj == null || StringUtil.isEmpty(curObjName)){
            populateError("curObj is null or curObjName is empty");
            return this;
        }
        // main
        try {
            Field field = curObj.getClass().getDeclaredField("offset");
            field.setAccessible(true);
            Object offsetObj = field.get(curObj);
            if(offsetObj == null){
                field.set(curObj, defaultOffset);
            }else{
                Long offset = Long.valueOf(offsetObj.toString());
                if(offset < 0){
                    populateError("value of '"+curObjName+"#offset' should more then '0'");
                    return this;
                }
            }
        } catch (NoSuchFieldException e) {
            populateError("there is not a filed named '"+curObjName+"#offset'");
            return this;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }


        try {
            Field field = curObj.getClass().getDeclaredField("limit");
            field.setAccessible(true);
            Object limitObj = field.get(curObj);
            if(limitObj == null){
                field.set(curObj, defaultLimit);
            }else{
                Integer limit = Integer.valueOf(limitObj.toString());
                if(limit < 1 || limit > 100){
                    populateError("value of '"+curObjName+"#limit' should between '1' and '100', [1,100]");
                    return this;
                }
            }
        } catch (NoSuchFieldException e) {
            populateError("there is not a filed named '"+curObjName+"#limit'");
            return this;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return this;
    }

    /**
     * 如果A的值为B，那么C的值必须为D
     * @param fieldNameA
     * @param expectValueB
     * @param fieldNameC
     * @param expectValueD
     * @return
     */
    public ValidUtil ifAIsBThenCMustD(String fieldNameA, Object expectValueB, String fieldNameC, Object expectValueD){
        // assert
        if(!this.valid || !notNull(fieldNameA, fieldNameC).isValid()){
            return this;
        }
        if(expectValueB == null || expectValueD ==null){
            populateError("expectValue of %s or expectValue of %s is null", fieldNameA, fieldNameC);
            return this;
        }
        // main
        if(JSON.toJSONString(expectValueB).equals(JSON.toJSONString(getMapValue(fieldNameA)))
                && !JSON.toJSONString(expectValueD).equals(JSON.toJSONString(getMapValue(fieldNameC)))){
            populateError("%s is %s, but %s is not %s", fieldNameA, JSON.toJSONString(expectValueB), fieldNameC, JSON.toJSONString(expectValueD));
        }
        return this;
    }

    /**
     * 如果A的值为B，那么C的值必须不能为D
     * @param fieldNameA
     * @param expectValueB
     * @param fieldNameC
     * @param expectValueD
     * @return
     */
    public ValidUtil ifAIsBThenCMustNotD(String fieldNameA, Object expectValueB, String fieldNameC, Object expectValueD){
        // assert
        if(!this.valid || !notNull(fieldNameA, fieldNameC).isValid()){
            return this;
        }
        if(expectValueB == null || expectValueD ==null){
            populateError("expectValue of %s or expectValue of %s is null", fieldNameA, fieldNameC);
            return this;
        }
        // main
        if(JSON.toJSONString(expectValueB).equals(JSON.toJSONString(getMapValue(fieldNameA)))
                && JSON.toJSONString(expectValueD).equals(JSON.toJSONString(getMapValue(fieldNameC)))){
            populateError("%s is %s, but %s is %s", fieldNameA, JSON.toJSONString(expectValueB), fieldNameC, JSON.toJSONString(expectValueD));
        }
        return this;
    }


    /**
     * 填充BaseError异常
     * @param msg
     */
    private void populateError(String msg, String... params){
        msg = String.format(msg, params);
        setError(ErrorEnum.PARAM_ERROR.getCode(), msg);
    }

    public boolean isValid() {
        return valid;
    }

    public BaseError getError() {
        return error;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public void setError(BaseError error) {
        this.setValid(false);
        this.error = error;
    }

    public void setError(Integer code, String message) {
        this.setValid(false);
        this.error.setCode(code);
        this.error.setMessage(message);
    }

    private Map<String, Object> getMap(){
        ValidObj curValidObj = objStack.peek();
        return curValidObj.getFieldsData();
    }

    private void setMap(Map<String, Object> map){
        ValidObj curValidObj = objStack.peek();
        curValidObj.setFieldsData(map);
    }

    private void setMapValue(String fieldName, Object obj){
        ValidObj curValidObj = objStack.peek();
        Map<String, Object> fieldsData = curValidObj.getFieldsData();
        if(fieldsData == null){
            fieldsData = new ConcurrentHashMap<>();
        }
        fieldsData.put(fieldName, obj);
        curValidObj.setFieldsData(fieldsData);
    }

    private Object getMapValue(String fieldName){
        ValidObj curValidObj = objStack.peek();
        Map<String, Object> fieldsData = curValidObj.getFieldsData();
        if(fieldsData == null){
            notNull(fieldName);
        }
        return fieldsData.get(fieldName);
    }
}