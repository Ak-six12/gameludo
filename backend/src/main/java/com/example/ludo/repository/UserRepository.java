package com.example.ludo.repository;

import com.example.ludo.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public User findByEmail(String email){return findOne("select id,name,email,mobile,provider,provider_id,guest from users where email=?",email).orElse(null);}
    public User findByMobile(String mobile){return findOne("select id,name,email,mobile,provider,provider_id,guest from users where mobile=?",mobile).orElse(null);}
    public User findByProviderId(String provider,String providerId){return findOne("select id,name,email,mobile,provider,provider_id,guest from users where provider=? and provider_id=?",provider,providerId).orElse(null);}
    public User findById(String id){return findOne("select id,name,email,mobile,provider,provider_id,guest from users where id=?",id).orElse(null);}
    public User create(String name,String email,String mobile,String provider,String providerId,boolean guest){String id=UUID.randomUUID().toString();jdbc.update("insert into users(id,name,email,mobile,provider,provider_id,guest,created_at) values(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,name,email,mobile,provider,providerId,guest);return findById(id);}
    public User updateName(String id,String name){jdbc.update("update users set name=? where id=?",name,id);return findById(id);}
    private Optional<User> findOne(String sql,Object... args){return jdbc.query(sql,args,(rs,n)->new User(rs.getString("id"),rs.getString("name"),rs.getString("email"),rs.getString("mobile"),rs.getString("provider"),rs.getString("provider_id"),rs.getBoolean("guest"))).stream().findFirst();}
}
