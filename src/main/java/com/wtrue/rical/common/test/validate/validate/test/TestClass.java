package com.wtrue.rical.common.test.validate.validate.test;

import com.alibaba.fastjson.JSON;
import com.wtrue.rical.common.test.validate.validate.ValidateUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @description:
 * @author: meidanlong
 * @date: 2022/1/24 10:53 AM
 */
class TestClass {

    public static void main(String[] args) {
        Person person = new Person();
        person.setName("mdl");
        person.setSex(1);
        List<String> hobby = new ArrayList<>();
        hobby.add("lq");
        hobby.add("yy");
        person.setHobby(hobby);
        Work work1 = new Work();
        work1.setName("猫眼");
        work1.setWorkType(2);
        work1.setSalary(20000);
        Work work2 = new Work();
        work2.setName("当真");
        work2.setWorkType(1);
//        work2.setSalary(0);
        List<Work> works = new ArrayList<>();
        works.add(work1);
        works.add(work2);
        person.setWorks(works);

        ValidateUtil valid = new ValidateUtil()
                .obj("my", () -> person)
                .notNull("name")
                .notNull("hobby")
                .supSub("works")
                .notNull("name")
                .notNull("workType")
                .notNull("salary")
                .valid();
        System.out.println(JSON.toJSONString(valid));
    }
}
