package com.fota.trade.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class OkController {
    @RequestMapping("/ok")
    @ResponseBody
    public String ok() {
        return "ok";
    }
}
