<?php
header('Content-Type: application/json');
header('Cache-Control: no-store');
$token = trim(file_get_contents(dirname(__DIR__).'/private/item-unlocks-token'));
$headers=function_exists('getallheaders')?array_change_key_case(getallheaders(),CASE_LOWER):[];
$auth=$_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? ($headers['authorization'] ?? '');
if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !$token || !hash_equals('Bearer '.$token, $auth)) { http_response_code(403); exit('{"error":"forbidden"}'); }
$raw = file_get_contents('php://input', false, null, 0, 1048577);
$data = json_decode($raw, true);
if (strlen($raw)>1048576 || !is_array($data) || ($data['timezone']??'')!=='Europe/Warsaw' || !is_array($data['items']??null)) {http_response_code(400);exit('{"error":"invalid"}');}
$items=[];
foreach ($data['items'] as $id=>$rule) {
 if (!preg_match('/^[a-z0-9_.-]+:[a-z0-9_\/.+-]+$/D',$id) || !is_array($rule) || !is_string($rule['name']??null) || !is_string($rule['unlockAt']??null) || !preg_match('/^(now|never|[0-9]{4}-[0-9]{2}-[0-9]{2})$/D',$rule['unlockAt'])) {http_response_code(400);exit('{"error":"invalid rule"}');}
 $items[$id]=['name'=>mb_substr($rule['name'],0,160),'unlockAt'=>$rule['unlockAt']];
}
$out=['timezone'=>'Europe/Warsaw','active'=>true,'serverUpdatedAt'=>gmdate('c'),'items'=>$items];
$tmp=tempnam(__DIR__,'.unlocks-');
if ($tmp===false || file_put_contents($tmp,json_encode($out,JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT))===false || !rename($tmp,__DIR__.'/item-unlocks.json')) {http_response_code(500);exit('{"error":"write"}');}
chmod(__DIR__.'/item-unlocks.json',0644);echo '{"ok":true}';
