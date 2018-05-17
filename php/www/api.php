<?php
$str = 'Hello, World';
$key = '0011223344';
$hmac = base64_encode(hash_hmac('sha256', $str, $key, true));
$result = [ 'hmacValue' => $hmac ];
header('content-type: application/json');
echo json_encode($result);