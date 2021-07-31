package lab.nnverify.platform.verifyplatform.verifykit.winr;

import lab.nnverify.platform.verifyplatform.config.WebSocketSessionManager;
import lab.nnverify.platform.verifyplatform.models.WiNRVerification;
import lab.nnverify.platform.verifyplatform.services.VerificationService;
import lab.nnverify.platform.verifyplatform.verifykit.ResultManager;
import lab.nnverify.platform.verifyplatform.verifykit.TaskExecuteListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 这个类需要是线程安全的
 */
@Slf4j
@Service
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class WiNRKit {
    @Autowired
    VerificationService verificationService;

    @Autowired
    WiNRConfig wiNRConfig;

    @Autowired
    @Qualifier("wiNRResultManager") ResultManager wiNRResultManager;

    private int runStatus = 1;

    private WiNRVerification params = null;

    private WebSocketSession session = null;

    private int asyncCheck = 0;

    // todo 添加出现错误时不运行task的能力

    public WiNRKit() {
    }

    public void setParams(WiNRVerification params) {
        this.params = params;
    }

    private TaskExecuteListener taskExecuteListener = new TaskExecuteListener() {
        @Override
        public void beforeTaskExecute() {
            log.info("-----beforeWiNRTaskExecute-----");
            asyncCheck++;
            params.setStatus("running");
            if (verificationService.saveWiNRVerificationParams(params)) {
                log.info("write verification record to database");
            } else {
                log.error("fail to write verification record to database");
            }
            // 验证的测试图片信息存入数据库
            int successSaveCount = verificationService.saveTestImageOfVerification(params.getVerifyId(), params.getTestImageInfo());
            log.info("verification test images saved into database. " + successSaveCount + "/" + params.getTestImageInfo().keySet().size());
            log.info("the async check value is: " + asyncCheck);
        }

        @Override
        public void afterTaskExecute() {
            log.info("-----afterWiNRTaskExecute-----");
            // 创建一个锚点 方便之后通过verify_id查找文件
            String verifyId = params.getVerifyId();
            log.info("verify id after task: " + verifyId);
            if (!wiNRResultManager.createResultFileAnchor(verifyId)) {
                log.error("anchor create failed: result file anchor create failed, verifyId is " + verifyId);
            }
            if (!wiNRResultManager.createAdvExampleAnchor(verifyId)) {
                log.error("anchor create failed: advExample file anchor create failed, verifyId is " + verifyId);
            }
            if (runStatus == 0) {
                verificationService.finishVerificationUpdateStatus(verifyId, "success");
                try {
                    if (session != null) {
                        session.sendMessage(new TextMessage("verify success. verifyId:" + verifyId));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                verificationService.finishVerificationUpdateStatus(verifyId, "error");
                try {
                    if (session != null) {
                        session.sendMessage(new TextMessage("verify failed. verifyId:" + verifyId));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public Map<String, String> getResultSync() throws IOException {
        String verifyId = params.getVerifyId();
        if (!params.getStatus().equals("success")) {
            return new HashMap<>();
        }
        InputStreamReader file = wiNRResultManager.getResultFile(verifyId);
        if (file == null) {
            return new HashMap<>();
        }
        BufferedReader reader = new BufferedReader(file);
        String line;
        ArrayList<String> result = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            result.add(line);
        }
        String[] secondLastLine = result.get(result.size() - 2).split("\\s+");
        String[] lastLine = result.get(result.size() - 1).split("\\s+");
        HashMap<String, String> resultMap = new HashMap<>();
        for (int i = 0; i < secondLastLine.length; i++) {
            String key = secondLastLine[i];
            resultMap.put(key, lastLine[i]);
        }
        return resultMap;
    }

    public List<String> getAdvExample(int image_num) {
        String verifyId = params.getVerifyId();

        List<String> advExamples = wiNRResultManager.getAdvExample(verifyId, image_num);
        return advExamples;
    }

    public int testAsync() {
        session = WebSocketSessionManager.getSession(String.valueOf(params.getUserId()));
        // 没有获取到websocket session，完成执行之后无法通知浏览器端，目前先直接返回错误
//        if (session == null) {
//            return -100;
//        }
        new Thread(() -> {
            taskExecuteListener.beforeTaskExecute();
            task();
            taskExecuteListener.afterTaskExecute();
        }).start();
        return 1;
    }

    private void task() {
        String dataset = params.getDataset();
        String epsilon = params.getEpsilon();
        String model = wiNRConfig.getUploadModelFilepath() + params.getNetName();
        String imageNum = String.valueOf(params.getTestImageInfo().keySet().size());
        String jsonPath = params.getJsonPath();
        String pureConv = params.getPureConv();

        try {
            PrintWriter printWriter = new PrintWriter(wiNRConfig.getBasicPath() + "run.sh");
            String command = String.format(
                    "python main.py --dataset %s --netname %s --pure_conv %s --num_image %s --json_path %s --epsilon %s --verifyId %s",
                    dataset, model, pureConv, imageNum, jsonPath, epsilon, params.getVerifyId());
            log.info("the command is " + command);
            printWriter.write(command);
            printWriter.flush();
            printWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            log.error("fail to write into run.sh");
        }

        ProcessBuilder processBuilder = new ProcessBuilder("./run.sh");
        processBuilder.directory(new File(wiNRConfig.getBasicPath()));
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            try {
                runStatus = process.waitFor();
                log.info("runStatus: " + runStatus);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
