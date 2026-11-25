package vn.nhaminh.api.family;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FamilyRepository extends JpaRepository<Family, Long> {
    /** Mỗi dòng: [Family, số thành viên]. Dùng cho trang quản trị. */
    @Query("""
            select f, count(m) from Family f
            left join FamilyMembership m on m.family = f
            group by f
            order by f.name
            """)
    List<Object[]> findAllWithMemberCount();
}
