<%@ page contentType="text/html; charset=utf-8" session="false" trimDirectiveWhitespaces="true" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%--@elvariable id="persons" type="java.util.List<com.malt.springsessionjsp.Person>"--%>

<!DOCTYPE HTML>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>Person List</title>
</head>
<body>
<h1>Person List</h1>

<br/><br/>
<div>
    <table>
        <tr>
            <th>First Name</th>
            <th>Last Name</th>
        </tr>
        <c:forEach items="${persons}" var="person">
            <jsp:include page="person.jsp">
                <jsp:param name="firstname" value="${person.firstname}"/>
                <jsp:param name="lastname" value="${person.lastname}"/>
            </jsp:include>
        </c:forEach>
    </table>
</div>
</body>

</html>