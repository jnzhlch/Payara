import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import java.io.IOException;

/** M3-② 部署验证用的最小 Servlet: 打包为 hello.war (context root = hello), 端点 /hello/hello。 */
@WebServlet("/hello")
public class HelloServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain");
        resp.getWriter().println("hello-feature-web");
    }
}
