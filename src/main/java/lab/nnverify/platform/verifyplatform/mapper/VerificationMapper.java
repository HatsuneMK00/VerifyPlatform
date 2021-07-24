package lab.nnverify.platform.verifyplatform.mapper;

import lab.nnverify.platform.verifyplatform.models.AllParamsVerification;
import lab.nnverify.platform.verifyplatform.models.DeepCertVerification;
import lab.nnverify.platform.verifyplatform.models.WiNRVerification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface VerificationMapper {
    @Select("select verify_id,user_id,tool,num_of_image,net_name,norm,core,activation,is_cifar,is_tiny_image_net,epsilon,dataset,status,start_time " +
            "from verification where user_id=#{userId} order by start_time desc")
    List<AllParamsVerification> fetchVerificationByUserId(Integer userId);

    @Insert("insert into verification(verify_id,user_id,tool,epsilon,dataset,num_of_image,net_name,status,start_time) values " +
            "(#{verifyId},#{userId},#{tool},#{epsilon},#{dataset},#{numOfImage},#{netName},#{status},#{startTime})")
    int insertWiNRVerificationRecord(WiNRVerification params);

    @Insert("insert into verification(verify_id,user_id,tool,net_name,num_of_image,norm,core,activation,is_cifar,is_tiny_image_net,status,start_time) values " +
            "(#{verifyId},#{userId},#{tool},#{netName},#{numOfImage},#{norm},#{core},#{activation},#{isCifar},#{isTinyImageNet},#{status},#{startTime})")
    int insertDeepCertVerificationRecord(DeepCertVerification params);

    @Update("update verification set status=#{status} where verify_id=#{verifyId}")
    int updateVerificationRecordStatus(String verifyId, String status);

    @Select("select verify_id,user_id,tool,epsilon,dataset,num_of_image,net_name,status,start_time from verification where verify_id=#{verifyId}")
    WiNRVerification fetchWiNRVerificationById(String verifyId);

    @Select("select tool from verification where verify_id=#{verifyId}")
    String fetchVerificationToolById(String verifyId);

    @Select("select verify_id,user_id,tool,net_name,num_of_image,norm,core,activation,is_cifar,is_tiny_image_net,status,start_time " +
            "from verification where verify_id=#{verifyId}")
    DeepCertVerification fetchDeepCertVerificationById(String verifyId);
}
