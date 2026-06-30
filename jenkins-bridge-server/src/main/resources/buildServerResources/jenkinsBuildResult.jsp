<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<div class="jenkinsBuildResult">
  <strong>Jenkins build link:</strong>
  <a href="<c:out value="${jenkinsUrl}"/>" target="_blank" rel="noopener noreferrer"><c:out value="${jenkinsUrl}"/></a>
</div>
